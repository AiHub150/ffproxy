// lang: Kotlin, file: PacketRouter.kt, target: Android API 26+
// TCP path removed — FF TCP goes direct now, no intercept needed

package com.ffproxy

import java.util.concurrent.ConcurrentHashMap

object PacketRouter {

    private const val LOCAL_PROXY_PORT = FFVpnService.LOCAL_PROXY_PORT
    private const val FF_PORT          = FFVpnService.FF_SERVER_PORT
    private const val PROTO_UDP        = 17

    val destinations = ConcurrentHashMap<Int, Pair<ByteArray, Int>>()

    fun route(packet: ByteArray): ByteArray? {
        if (packet.size < 20) return packet

        val ipVersion = (packet[0].toInt() shr 4) and 0xF
        if (ipVersion != 4) return packet

        val ihl = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ihl + 8) return packet

        val proto = packet[9].toInt() and 0xFF
        if (proto != PROTO_UDP) return packet      // TCP and everything else — pass

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or
                      (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != FF_PORT) return packet      // wrong UDP port — pass

        val srcPort   = ((packet[ihl].toInt() and 0xFF) shl 8) or
                        (packet[ihl + 1].toInt() and 0xFF)
        val origDstIp = byteArrayOf(packet[16], packet[17], packet[18], packet[19])
        destinations[srcPort] = Pair(origDstIp, dstPort)

        val modified = packet.copyOf()
        modified[16] = 10; modified[17] = 0; modified[18] = 0; modified[19] = 1
        modified[ihl + 2] = (LOCAL_PROXY_PORT shr 8).toByte()
        modified[ihl + 3] = (LOCAL_PROXY_PORT and 0xFF).toByte()
        recomputeIpChecksum(modified, ihl)
        return modified
    }

    private fun recomputeIpChecksum(packet: ByteArray, ihl: Int) {
        packet[10] = 0; packet[11] = 0
        var sum = 0
        for (i in 0 until ihl step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv() and 0xFFFF
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }
}
