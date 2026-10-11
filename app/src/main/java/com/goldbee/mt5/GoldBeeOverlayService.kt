package com.goldbee.mt5

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Movable read-only overlay for using Gold Bee alongside MT5. Never submits orders. */
class GoldBeeOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var detail: TextView? = null
    private var compact = false
    private var layoutParams: WindowManager.LayoutParams? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (root == null) return
            refreshPanel()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createPanel()
        handler.post(refreshRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createPanel() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 29, 38))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(199, 158, 72))
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "GOLD-BEE · MT5"
            setTextColor(Color.rgb(240, 195, 88))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(8), 0)
        }
        val collapse = smallButton("−") {
            compact = !compact
            refreshPanel()
        }
        val close = smallButton("×") { stopSelf() }
        header.addView(title, LinearLayout.LayoutParams(0, dp(32), 1f))
        header.addView(collapse)
        header.addView(close)
        card.addView(header)

        detail = TextView(this).apply {
            text = "等待 MT5 只读报价…"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, dp(5), 0, dp(6))
        }
        card.addView(detail)
        card.addView(smallButton("打开 Gold-bee") {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(launch)
            }
        })
        root = card

        val params = WindowManager.LayoutParams(
            dp(286),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(110)
        }
        layoutParams = params
        enableDragging(header, params)
        windowManager?.addView(card, params)
    }

    private fun smallButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(6, 0, 6, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { action() }
        }

    private fun enableDragging(view: View, params: WindowManager.LayoutParams) {
        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX.toInt()
                    downY = event.rawY.toInt()
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + event.rawX.toInt() - downX
                    params.y = startY + event.rawY.toInt() - downY
                    runCatching { windowManager?.updateViewLayout(root, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun refreshPanel() {
        val text = detail ?: return
        val settings = getSharedPreferences("gold_bee_settings", MODE_PRIVATE)
        val observed = getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
        val bid = observed.getString(Mt5ScreenAccessibilityService.KEY_BID, "").orEmpty().toDoubleOrNull()
        val ask = observed.getString(Mt5ScreenAccessibilityService.KEY_ASK, "").orEmpty().toDoubleOrNull()
        val symbol = observed.getString(Mt5ScreenAccessibilityService.KEY_SYMBOL, "").orEmpty()
        val source = observed.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val timestamp = observed.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        val age = if (timestamp > 0L) System.currentTimeMillis() - timestamp else Long.MAX_VALUE
        val normalized = symbol.uppercase(Locale.US).replace("/", "").replace(".", "").replace("_", "")
        val quoteValid = (source == "ACCESSIBILITY" || source == "SCREEN_OCR") &&
            (normalized == "XAUUSD" || normalized.startsWith("XAUUSD") || normalized == "GOLD") &&
            bid != null && ask != null && bid.isFinite() && ask.isFinite() &&
            bid > 0.0 && ask > bid && age in 0L..3_000L

        val decision = settings.getString("overlay_decision", "尚未分析").orEmpty()
        val plan = settings.getString("overlay_plan", "Entry —  |  SL —  |  TP —").orEmpty()
        val reason = settings.getString("overlay_reason", "打开 Gold-bee 执行分析。").orEmpty()
        val monitor = settings.getString("paper_monitor_status", "尚无模拟持仓监控状态。").orEmpty()
        text.text = if (compact) {
            if (quoteValid) "XAUUSD  ${fmt(bid!!)} / ${fmt(ask!!)} · ${age}ms" else "XAUUSD · 报价等待/过期"
        } else {
            buildString {
                appendLine(if (quoteValid) "XAUUSD  ${fmt(bid!!)} / ${fmt(ask!!)}" else "MT5 报价：等待 / 过期 / 品种未确认")
                appendLine(if (quoteValid) "点差：${fmt(ask!! - bid!!)} · ${age} ms" else "切回 MT5 并确认只读读取/OCR仍在运行")
                appendLine("决策：${decision}")
                appendLine(plan)
                appendLine("理由：${reason.take(120)}")
                append("模拟监控：${monitor.take(100)}")
            }
        }
        text.setTextColor(if (quoteValid) Color.WHITE else Color.rgb(255, 190, 110))
        val params = layoutParams
        val panel = root
        if (params != null && panel != null) {
            text.visibility = if (compact) View.GONE else View.VISIBLE
            if (panel.childCount >= 3) panel.getChildAt(2).visibility = if (compact) View.GONE else View.VISIBLE
            runCatching { windowManager?.updateViewLayout(panel, params) }
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        root?.let { view -> runCatching { windowManager?.removeView(view) } }
        root = null
        detail = null
        windowManager = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.goldbee.mt5.STOP_OVERLAY"
    }
}
