// lang: Kotlin, file: ModMenuOverlay.kt, target: Android API 26+

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
    private var espView: View? = null
    private var menuVisible = false

    var aimbotEnabled = false
    var espEnabled = false

    // screen dimensions for projection
    private val dm = context.resources.displayMetrics
    private val screenW = dm.widthPixels.toFloat()
    private val screenH = dm.heightPixels.toFloat()

    // ESP paints
    private val boxPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val hpPaint = Paint().apply {
        isAntiAlias = true
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val hpBgPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
    }
    private val namePaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val snapPaint = Paint().apply {
        isAntiAlias = true
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() {
            espView?.invalidate()
            if (aimbotEnabled) runAimbot()
            handler.postDelayed(this, 33) // ~30fps
        }
    }

    fun show() {
        if (floatView != null) return
        startEspOverlay()
        startFloatButton()
        handler.post(refreshTick)
    }

    private fun startEspOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val canvas = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                if (!espEnabled) return
                val count = EntityStore.count
                for (i in 0 until count) {
                    val e = EntityStore.entities[i] ?: continue
                    val screen = worldToScreen(e.x, e.y, e.z) ?: continue

                    // box — derived from distance-based size estimate
                    val dist   = distanceToEntity(e)
                    val height = (screenH * 0.15f * (10f / dist.coerceAtLeast(1f))).coerceIn(40f, 300f)
                    val width  = height * 0.5f
                    val sx     = screen.first
                    val sy     = screen.second

                    val left   = sx - width / 2
                    val top    = sy - height
                    val right  = sx + width / 2
                    val bottom = sy

                    canvas.drawRect(left, top, right, bottom, boxPaint)

                    // HP bar — left of box
                    val hpRatio = (e.hp / 200f).coerceIn(0f, 1f)
                    val barW    = 6f
                    val barX    = left - 10f
                    canvas.drawRect(barX, top, barX + barW, bottom, hpBgPaint)
                    canvas.drawRect(barX, top + height * (1f - hpRatio), barX + barW, bottom, hpPaint)

                    // HP text
                    namePaint.color = Color.WHITE
                    canvas.drawText("HP ${e.hp.toInt()}", sx, top - 8f, namePaint)

                    // snapline to feet from screen center bottom
                    if (aimbotEnabled) {
                        canvas.drawLine(screenW / 2, screenH, sx, bottom, snapPaint)
                    }
                }
            }
        }

        espView = canvas
        wm.addView(canvas, params)
    }

    private fun startFloatButton() {
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

        circle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX; lastY = event.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        moved = true
                        params.x += dx.toInt(); params.y += dy.toInt()
                        wm.updateViewLayout(floatView, params)
                        lastX = event.rawX; lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) toggleMenu(); true }
                else -> false
            }
        }

        floatView = circle
        wm.addView(circle, params)
    }

    // ── Aimbot ───────────────────────────────────────────────────────────────

    private fun runAimbot() {
        // pick closest enemy to screen center
        var bestDist = Float.MAX_VALUE
        var bestEntity: EntityStore.Entity? = null

        for (i in 0 until EntityStore.count) {
            val e = EntityStore.entities[i] ?: continue
            val screen = worldToScreen(e.x, e.y, e.z) ?: continue
            val dx = screen.first  - screenW / 2
            val dy = screen.second - screenH / 2
            val d  = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                bestEntity = e
            }
        }

        bestEntity ?: return

        // aim at head — offset y up by ~15% of estimated box height
        val dist   = distanceToEntity(bestEntity)
        val height = (screenH * 0.15f * (10f / dist.coerceAtLeast(1f))).coerceIn(40f, 300f)
        val screen = worldToScreen(bestEntity.x, bestEntity.y, bestEntity.z) ?: return
        val headY  = screen.second - height * 0.85f  // top of box ≈ head

        injectAim(screen.first, headY)
    }

    private fun injectAim(x: Float, y: Float) {
        // inject touch event to move crosshair to head position
        val downTime = android.os.SystemClock.uptimeMillis()
        val inst = android.app.Instrumentation()
        try {
            inst.sendPointerSync(
                MotionEvent.obtain(downTime, downTime,
                    MotionEvent.ACTION_MOVE, x, y, 0)
            )
        } catch (e: Exception) {}
    }

    // ── Projection ───────────────────────────────────────────────────────────

    /**
     * Naive linear projection — maps world coords to screen.
     * FF uses a ~60° FOV. Without the real view-projection matrix
     * this is an approximation; replace with matrix read from memory
     * for pixel-accurate placement.
     */
    private fun worldToScreen(wx: Float, wy: Float, wz: Float): Pair<Float, Float>? {
        // placeholder camera — assumes player at origin looking down +Z
        // real impl: read camera pos/matrix from game memory via /proc/pid/mem
        val camZ = 0f
        val dz   = wz - camZ
        if (dz <= 0f) return null  // behind camera

        val fov    = 60f
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val scale  = (screenH / 2f) / Math.tan(fovRad / 2.0).toFloat()

        val sx = screenW / 2f + (wx / dz) * scale
        val sy = screenH / 2f - (wy / dz) * scale

        if (sx < 0 || sx > screenW || sy < 0 || sy > screenH) return null
        return Pair(sx, sy)
    }

    private fun distanceToEntity(e: EntityStore.Entity): Float {
        return Math.sqrt((e.x * e.x + e.y * e.y + e.z * e.z).toDouble()).toFloat()
    }

    // ── Menu ─────────────────────────────────────────────────────────────────

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
            espView?.invalidate()
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
        handler.removeCallbacks(refreshTick)
        hideMenu()
        espView?.let { wm.removeView(it) }
        espView = null
        floatView?.let { wm.removeView(it) }
        floatView = null
    }
}
