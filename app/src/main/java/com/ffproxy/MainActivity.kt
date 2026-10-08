package com.ffproxy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {

    private val VPN_REQUEST_CODE = 1
    private val OVERLAY_REQUEST_CODE = 2
    private lateinit var status: TextView
    private var overlay: ModMenuOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 120, 60, 60)
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))

        val title = TextView(this)
        title.text = "FF MOD"
        title.textSize = 32f
        title.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD

        status = TextView(this)
        status.text = "Status: Stopped"
        status.textSize = 16f
        status.setTextColor(android.graphics.Color.LTGRAY)
        status.setPadding(0, 20, 0, 40)

        val startBtn = Button(this)
        startBtn.text = "START"
        startBtn.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
        startBtn.setTextColor(android.graphics.Color.WHITE)
        startBtn.setOnClickListener { checkAndStart() }

        val stopBtn = Button(this)
        stopBtn.text = "STOP"
        stopBtn.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
        stopBtn.setTextColor(android.graphics.Color.WHITE)
        stopBtn.setPadding(0, 20, 0, 0)
        stopBtn.setOnClickListener {
            stopService(Intent(this, FFVpnService::class.java))
            overlay?.dismiss()
            overlay = null
            status.text = "Status: Stopped"
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)
    }

    private fun checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivityForResult(intent, OVERLAY_REQUEST_CODE)
            return
        }
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            startAll()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_REQUEST_CODE -> checkAndStart()
            VPN_REQUEST_CODE -> if (resultCode == RESULT_OK) startAll()
        }
    }

    private fun startAll() {
        startService(Intent(this, FFVpnService::class.java))
        overlay = ModMenuOverlay(applicationContext)
        overlay?.show()
        status.text = "Status: Running"
    }
}
