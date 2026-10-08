// lang: Kotlin, file: ProxyCore.kt, target: Android API 26+

package com.ffproxy

import android.net.VpnService
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean

object ProxyCore {

    private val running    = AtomicBoolean(false)
    private var localSocket: DatagramSocket? = null
    private var outSocket:   DatagramSocket? = null

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

                    val data    = pkt.data.copyOf(pkt.length)
                    PacketParser.parseInbound(data)

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

        // TCP relay: accepts rewritten TCP from tun, looks up real dst, forwards direct
        Thread {
            try {
                val tcpServer = ServerSocket(10013, 50, InetAddress.getByName("10.0.0.1"))
                while (running.get()) {
                    val client = try { tcpServer.accept() } catch (_: Exception) { break }
                    Thread {
                        try {
                            val srcPort = client.port

                            // look up real dst captured on SYN
                            val dst     = PacketRouter.tcpDestinations[srcPort]
                            val dstAddr = if (dst != null) InetAddress.getByAddress(dst.first)
                                          else InetAddress.getByName("202.81.96.54")
                            val dstPort = dst?.second ?: 443

                            val remote = Socket()
                            svc.protect(remote)
                            remote.connect(InetSocketAddress(dstAddr, dstPort), 5000)

                            val c2r = Thread {
                                try { client.inputStream.copyTo(remote.outputStream) }
                                catch (_: Exception) {}
                            }
                            val r2c = Thread {
                                try { remote.inputStream.copyTo(client.outputStream) }
                                catch (_: Exception) {}
                            }
                            c2r.start(); r2c.start()
                            c2r.join();  r2c.join()
                        } catch (_: Exception) {
                        } finally {
                            try { client.close() } catch (_: Exception) {}
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
