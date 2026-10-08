// lang: Kotlin, file: ProxyCore.kt — full replacement

package com.ffproxy

import android.net.VpnService
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean

object ProxyCore {

    private val running = AtomicBoolean(false)
    private var localSocket: DatagramSocket? = null
    private var outSocket: DatagramSocket? = null

    fun start(svc: FFVpnService, localPort: Int) {
        running.set(true)
        localSocket = DatagramSocket(localPort)
        outSocket = DatagramSocket().also { svc.protect(it) }

        var clientAddr: InetAddress? = null
        var clientPort = 0

        // inbound from tun → out to real server
        Thread {
            val buf = ByteArray(65535)
            while (running.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    localSocket!!.receive(pkt)
                    clientAddr = pkt.address
                    clientPort = pkt.port

                    val data = pkt.data.copyOf(pkt.length)
                    PacketParser.parseInbound(data)

                    // look up original dst for this src port
                    val dst = PacketRouter.destinations[clientPort]
                    val dstAddr = if (dst != null)
                        InetAddress.getByAddress(dst.first)
                    else
                        InetAddress.getByName("185.60.112.157")   // fallback
                    val dstPort = dst?.second ?: FFVpnService.FF_SERVER_PORT

                    outSocket!!.send(DatagramPacket(data, data.size, dstAddr, dstPort))
                } catch (e: Exception) { continue }
            }
        }.start()

        // inbound from real server → back to game
        Thread {
            val buf = ByteArray(65535)
            while (running.get()) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    outSocket!!.receive(pkt)
                    val data = pkt.data.copyOf(pkt.length)
                    PacketParser.parseOutbound(data)
                    if (clientAddr != null) {
                        localSocket!!.send(DatagramPacket(data, data.size, clientAddr, clientPort))
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
