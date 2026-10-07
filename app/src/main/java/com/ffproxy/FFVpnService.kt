package com.ffproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

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
            .setContentText("Proxy running")
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
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setSession("FFProxy")
            .setMtu(1500)

        tun = builder.establish() ?: return
        running = true

        val input = FileInputStream(tun!!.fileDescriptor)
        val output = FileOutputStream(tun!!.fileDescriptor)

        Thread {
            ProxyCore.start(this, LOCAL_PROXY_PORT)
        }.start()

        Thread {
            val buf = ByteArray(32767)
            while (running) {
                val len = input.read(buf)
                if (len <= 0) continue
                val packet = buf.copyOf(len)
                val modified = PacketRouter.route(packet)
                if (modified != null) {
                    output.write(modified)
                } else {
                    output.write(packet)
                }
            }
        }.start()
    }

    override fun onDestroy() {
        running = false
        tun?.close()
        ProxyCore.stop()
    }
}
