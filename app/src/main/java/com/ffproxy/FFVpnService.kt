// lang: Kotlin, file: FFVpnService.kt, target: Android API 26+

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
    @Volatile private var running = false

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

        // system apps bypass tunnel — their traffic is irrelevant to us
        listOf(
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending"
        ).forEach { try { builder.addDisallowedApplication(it) }
