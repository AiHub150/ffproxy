// lang: Kotlin, file: ProxyCore.kt, target: Android API 26+

package com.ffproxy

import android.net.VpnService
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean

object ProxyCore {

    private val running     = AtomicBoolean(false)
    private var localSocket: DatagramSocket? = null
    private var outSocket:   DatagramSocket? = null

    // FF game server — real address, not through tunnel
    private const val FF_REAL_HOST = "185.60.112.157"

    fun start(svc: FFVpnService, localPort: Int) {
        running.set(true)

        // localSocket receives rewritten UDP from tun (game → proxy)
        localSocket = DatagramSocket(localPort)

        // outSocket bypasses tunnel, goes direct to FF server
        outSocket = DatagramSocket().also { svc.protect(it) }

        var clientAddr: InetAddress? = null
        var clientPort = 0

        // game → real server
        Thread {
            val buf = ByteArray(65535)
            while (running.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    localSocket!!.receive(pkt)
                    clientAddr = pkt.address
                    clientPort = pkt.port

                    val data    = pkt.data.copyOf(pkt.length)
                    PacketParser.parseInbound(data)

                    val dst     = PacketRouter.destinations[clientPort]
                    val dstAddr = if (dst != null) InetAddress.getByAddress(dst.first)
                                  else InetAddress.getByName(FF_REAL_HOST)
                    val dstPort = dst?.second ?: FFVpnService.FF_SERVER_PORT

                    outSocket!!.send(DatagramPacket(data, data.size, dstAddr, dstPort))
                } catch (e: Exception) { continue }
            }
        }.start()

        // real server → game
        Thread {
            val buf = ByteArray(65535)
            while (running.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    outSocket!!.receive(pkt)
                    val data = pkt.data.copyOf(pkt.length)
                    PacketParser.parseOutbound(data)
                    clientAddr?.let {
                        localSocket!!.send(DatagramPacket(data, data.size, it, clientPort))
                    }
                } catch (e: Exception) { continue }
            }
        }.start()
    }

    fun stop() {
        running.set(false)
        localSocket?.close()
        outSocket?.close()
    }
}
