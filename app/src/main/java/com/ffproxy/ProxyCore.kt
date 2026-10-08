// lang: Kotlin, file: ProxyCore.kt, target: Android API 26+

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
        outSocket   = DatagramSocket().also { svc.protect(it) }

        var clientAddr: InetAddress? = null
        var clientPort = 0

        // UDP: game → proxy → real server
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

                    // use saved original dst, fall back to known garena addr
                    val dst     = PacketRouter.destinations[clientPort]
                    val dstAddr = if (dst != null) InetAddress.getByAddress(dst.first)
                                  else InetAddress.getByName("185.60.112.157")
                    val dstPort = dst?.second ?: FFVpnService.FF_SERVER_PORT

                    outSocket!!.send(DatagramPacket(data, data.size, dstAddr, dstPort))
                } catch (_: Exception) { continue }
            }
        }.start()

        // UDP: real server → proxy → game
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
                } catch (_: Exception) { continue }
            }
        }.start()

        // TCP relay: handles FF config/version/auth HTTPS — forwards direct, bypasses tunnel
        Thread {
            try {
                val tcpServer = ServerSocket(10013, 50, InetAddress.getByName("10.0.0.1"))
                while (running.get()) {
                    val client = tcpServer.accept()
                    Thread {
                        try {
                            // forward to FF config server direct via protected socket
                            val remote = Socket()
                            svc.protect(remote)
                            remote.connect(InetSocketAddress("202.81.96.54", 443), 5000)
                            val c2r = Thread { client.inputStream.copyTo(remote.outputStream) }
                            val r2c = Thread { remote.inputStream.copyTo(client.outputStream) }
                            c2r.start(); r2c.start()
                            c2r.join(); r2c.join()
                        } catch (_: Exception) {
                        } finally {
                            client.close()
                        }
                    }.start()
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun stop() {
        running.set(false)
        localSocket?.close()
        outSocket?.close()
    }
}
