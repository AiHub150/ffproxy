package com.ffproxy

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 120, 60, 60)
        }

        val status = TextView(this).apply {
            text = "FFProxy — stopped"
            textSize = 18f
        }

        val startBtn = Button(this).apply {
            text = "START PROXY"
            setOnClickListener {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent != null) {
                    startActivityForResult(intent, VPN_REQUEST_CODE)
                } else {
                    startProxy()
                    status.text = "FFProxy — running"
                }
            }
        }

        val stopBtn = Button(this).apply {
            text = "STOP PROXY"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FFVpnService::class.java))
                status.text = "FFProxy — stopped"
            }
        }

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
        val intent = Intent(this, FFVpnService::class.java)
        startForegroundService(intent)
    }
}
