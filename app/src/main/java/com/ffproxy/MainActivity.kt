package com.ffproxy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {

    private val VPN_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 120, 60, 60)
        layout.setBackgroundColor(android.graphics.Color.WHITE)

        val title = TextView(this)
        title.text = "FFProxy"
        title.textSize = 28f
        title.setTextColor(android.graphics.Color.BLACK)

        val status = TextView(this)
        status.text = "Status: Stopped"
        status.textSize = 16f
        status.setTextColor(android.graphics.Color.DKGRAY)
        status.setPadding(0, 20, 0, 20)

        val startBtn = Button(this)
        startBtn.text = "START"
        startBtn.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                startProxy()
                status.text = "Status: Running"
            }
        }

        val stopBtn = Button(this)
        stopBtn.text = "STOP"
        stopBtn.setOnClickListener {
            stopService(Intent(this, FFVpnService::class.java))
            status.text = "Status: Stopped"
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(startBtn)
        layout.addView(stopBtn)

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startProxy()
        }
    }

    private fun startProxy() {
        startService(Intent(this, FFVpnService::class.java))
    }
}
