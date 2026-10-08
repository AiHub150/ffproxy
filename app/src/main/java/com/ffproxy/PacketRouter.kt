// lang: Kotlin, file: PacketRouter.kt, target: Android API 26+

package com.ffproxy

import java.util.concurrent.ConcurrentHashMap

object PacketRouter {

    private const val LOCAL_PROXY_PORT = FFVpnService.LOCAL_PROXY_PORT
    private const val FF_PORT          = FFVpnService.FF_SERVER_PORT
    private const val TCP_RELAY_PORT   = 10013
    private const val PROTO_UDP        = 17
    private const val PROTO_TCP        = 6

    // UDP: srcPort → (originalDstIp, originalDstPort)
    val destinations = ConcurrentHashMap<Int, Pair<ByteArray, Int>>()

    // TCP: srcPort → (originalDstIp, originalDstPort) — captured on SYN
    val tcpDestinations = ConcurrentHashMap<Int, Pair<ByteArray, Int>>()

    fun route(packet: ByteArray): ByteArray? {
        if (packet.size < 20) return packet

        val ipVersion = (packet[0].toInt() shr 4) and 0xF
        if (ipVersion != 4) return packet

        val proto = packet[9].toInt() and 0xFF
        val ihl   = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ihl + 8) return packet

        return when (proto) {
            PROTO_UDP -> routeUdp(packet, ihl)
            PROTO_TCP -> routeTcp(packet, ihl)
            else      -> packet
        }
    }

    private fun routeUdp(packet: ByteArray, ihl: Int): ByteArray {
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                      (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != FF_PORT) return packet

        val srcPort    = ((packet[ihl].toInt() and 0xFF) shl 8) or
                         (packet[ihl + 1].toInt() and 0xFF)
        val origDstIp  = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        destinations[srcPort] = Pair(origDstIp, dstPort)

        val modified = packet.copyOf()
        modified[16] = 10; modified[17] = 0; modified[18] = 0; modified[19] = 1
        modified[ihl + 2] = (LOCAL_PROXY_PORT shr 8).toByte()
        modified[ihl + 3] = (LOCAL_PROXY_PORT and 0xFF).toByte()
        recomputeIpChecksum(modified, ihl)
        return modified
    }

    private fun routeTcp(packet: ByteArray, ihl: Int): ByteArray {
        if (packet.size < ihl + 20) return packet

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                      (packet[ihl + 3].toInt() and 0xFF)
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or
                      (packet[ihl + 1].toInt() and 0xFF)
        val flags   = packet[ihl + 13].toInt() and 0xFF
        val isSyn   = (flags and 0x02) != 0 && (flags and 0x10) == 0  // SYN, not SYN-ACK

        // only intercept outbound TCP (not loopback returns from relay)
        val dstIp = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        val isLoopback = dstIp[0] == 10.toByte() && dstIp[1] == 0.toByte() &&
                         dstIp[2] == 0.toByte()

        if (isLoopback)
