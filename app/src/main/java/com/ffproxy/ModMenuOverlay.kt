package com.ffproxy

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*

class ModMenuOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatView: View? = null
    private var menuView: View? = null
    private var menuVisible = false

    var aimbotEnabled = false
    var espEnabled = false

    fun show() {
        if (floatView != null) return
        val params = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        val circle = object : View(context) {
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#CC2196F3")
                style = Paint.Style.FILL
            }
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                canvas.drawCircle(cx, cy, cx - 4, paint)
                canvas.drawText("FF", cx, cy + 10f, textPaint)
            }
        }

        var lastX = 0f
        var lastY = 0f
        var moved = false

        circle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        moved = true
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        wm.updateViewLayout(floatView, params)
                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleMenu()
                    true
                }
                else -> false
            }
        }

        floatView = circle
        wm.addView(circle, params)
    }

    private fun toggleMenu() {
        if (menuVisible) hideMenu() else showMenu()
    }

    private fun showMenu() {
        if (menuView != null) return
        menuVisible = true

        val params = WindowManager.LayoutParams(
            600, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 200

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 30, 30, 30)
        layout.setBackgroundColor(Color.parseColor("#DD000000"))

        val title = TextView(context)
        title.text = "FF MOD MENU"
        title.textSize = 18f
        title.setTextColor(Color.parseColor("#2196F3"))
        title.typeface = Typeface.DEFAULT_BOLD
        title.setPadding(0, 0, 0, 20)
        layout.addView(title)

        layout.addView(makeToggle("Aimbot") { enabled ->
            aimbotEnabled = enabled
        })

        layout.addView(makeToggle("ESP / Wallhack") { enabled ->
            espEnabled = enabled
        })

        val closeBtn = Button(context)
        closeBtn.text = "CLOSE"
        closeBtn.setTextColor(Color.WHITE)
        closeBtn.setBackgroundColor(Color.parseColor("#882196F3"))
        closeBtn.setOnClickListener { hideMenu() }
        layout.addView(closeBtn)

        menuView = layout
        wm.addView(layout, params)
    }

    private fun makeToggle(label: String, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 10, 0, 10)

        val txt = TextView(context)
        txt.text = label
        txt.textSize = 15f
        txt.setTextColor(Color.WHITE)
        txt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(txt)

        val toggle = Switch(context)
        toggle.isChecked = false
        toggle.setOnCheckedChangeListener { _, checked ->
            onChange(checked)
            txt.setTextColor(if (checked) Color.parseColor("#4CAF50") else Color.WHITE)
        }
        row.addView(toggle)

        return row
    }

    private fun hideMenu() {
        menuVisible = false
        menuView?.let { wm.removeView(it) }
        menuView = null
    }

    fun dismiss() {
        hideMenu()
        floatView?.let { wm.removeView(it) }
        floatView = null
    }
}
