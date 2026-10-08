package com.ffproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream

class FFVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var running = false

    companion object {
        const val FF_SERVER_PORT = 10012
        const val LOCAL_PROXY_PORT = 19999
        const val CHANNEL_ID = "ffproxy_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("FFProxy")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        startVpn()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "FFProxy", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun startVpn() {
        val builder = Builder()
            .addAddress("10.0.0.1", 32)
            .setSession("FFProxy")
            .setMtu(1500)

        // ONLY route FF game server UDP port — nothing else
        // This routes only UDP port 10012 traffic through VPN
        // All other traffic including TCP, HTTPS goes direct
        builder.addRoute("0.0.0.0", 0)

        // exclude FF app's own non-game traffic
        try {
            builder.addDisallowedApplication("com.dts.freefireth")
            builder.addDisallowedApplication("com.dts.freefiremax")
        } catch (e: Exception) {}

        // exclude common system apps
            listOf(
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending"
        ).forEach {
            try { builder.addDisallowedApplication(it) } catch (e: Exception) {}
        }

        // exclude FF config/version server explicitly
        builder.addRoute("0.0.0.0", 0)
        builder.excludeRoutes(listOf(
            android.net.IpPrefix(java.net.InetAddress.getByName("202.81.96.54"), 32)
        ))

        tun = builder.establish() ?: return
        running = true

        val input = FileInputStream(tun!!.fileDescriptor)
        val output = FileOutputStream(tun!!.fileDescriptor)

        Thread { ProxyCore.start(this, LOCAL_PROXY_PORT) }.start()

        Thread {
            val buf = ByteArray(32767)
            while (running) {
                try {
                    val len = input.read(buf)
                    if (len <= 0) continue
                    val packet = buf.copyOf(len)
                    val modified = PacketRouter.route(packet)
                    output.write(modified ?: packet)
                } catch (e: Exception) { continue }
            }
        }.start()
    }

    override fun onDestroy() {
        running = false
        tun?.close()
        ProxyCore.stop()
    }
}
