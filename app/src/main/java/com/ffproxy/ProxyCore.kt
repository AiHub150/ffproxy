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

        val garenaAddr = InetAddress.getByName("185.60.112.157")
        val garenaPort = FFVpnService.FF_SERVER_PORT

        var clientAddr: InetAddress? = null
        var clientPort = 0

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
                    outSocket!!.send(DatagramPacket(data, data.size, garenaAddr, garenaPort))
                } catch (e: Exception) { continue }
            }
        }.start()

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
