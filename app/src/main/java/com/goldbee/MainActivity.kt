package com.goldbee

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.goldbee.market.RealMarketPrice
import com.goldbee.market.RealMarketRestClient
import com.goldbee.settings.EncryptedApiKeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val bg = Color.rgb(9, 12, 18)
    private val panel = Color.rgb(19, 24, 34)
    private val gold = Color.rgb(255, 193, 7)
    private val white = Color.rgb(240, 243, 250)
    private val muted = Color.rgb(145, 155, 173)
    private val green = Color.rgb(58, 210, 145)
    private val red = Color.rgb(255, 103, 112)

    private val client = RealMarketRestClient()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("gold_bee_settings", MODE_PRIVATE)
    }

    private lateinit var apiKeyInput: EditText
    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var bidAskText: TextView
    private lateinit var candleText: TextView
    private lateinit var updateText: TextView
    private lateinit var modeText: TextView

    private var polling = false
    private var requestInProgress = false

    private val pollInterval = 10 * 60 * 1000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return

            fetchPrice()

            handler.postDelayed(this, pollInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildScreen()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            setPadding(0, dp(3), 0, dp(3))
        }
    }

    private fun makeCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            setBackgroundColor(panel)
            layoutParams = LinearLayout.LayoutParams(
                -1, -2
            ).apply {
                bottomMargin = dp(12)
            }
        }

    private fun addLabel(
        parent: LinearLayout,
        value: String,
        size: Float = 13f,
        color: Int = muted,
        bold: Boolean = false
    ): TextView {
        val view = makeText(value, size, color, bold)
        parent.addView(
            view,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            }
        )
        return view
    }

    private fun addButton(
        parent: LinearLayout,
        value: String,
        primary: Boolean = false,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            setTextColor(if (primary) bg else white)
            setBackgroundColor(if (primary) gold else Color.rgb(39, 46, 59))
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                0, dp(48), 1f
            ).apply {
                marginEnd = dp(6)
            }
        )
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(22))
        }

        scroll.addView(root)
        setContentView(scroll)

        addLabel(root, "GOLD BEE", 27f, gold, true)
        addLabel(root, "XAUUSD · MARKET ANALYSIS TERMINAL", 11f)
        addLabel(
            root,
            "真实行情接入测试版",
            13f,
            white
        )

        // API Key settings
        val settings = makeCard()
        addLabel(settings, "行情连接设置", 17f, white, true)
        addLabel(
            settings,
            "填写 RealMarketAPI 的 API Key。密钥只保存在此设备的应用设置中。",
            12f
        )

        apiKeyInput = EditText(this).apply {
            hint = "输入 API Key"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(31, 37, 49))
        }

        settings.addView(
            apiKeyInput,
            LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(10)
            }
        )

        // Migrate any previously saved plaintext key into Android Keystore-backed storage.
        val legacyKey = prefs.getString("api_key", "").orEmpty()
        if (legacyKey.isNotBlank()) {
            EncryptedApiKeyStore.save(this, "realmarket_api_key", legacyKey)
            prefs.edit().remove("api_key").apply()
        }

        val savedKey = EncryptedApiKeyStore.get(this, "realmarket_api_key")
        if (savedKey.isNotBlank()) {
            apiKeyInput.hint = "已安全保存 API Key；留空可继续使用"
        }

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addButton(saveRow, "保存并测试", true) {
            val entered = apiKeyInput.text.toString().trim()

            if (entered.isNotBlank()) {
                EncryptedApiKeyStore.save(this, "realmarket_api_key", entered)
                prefs.edit().remove("api_key").apply()
            }

            if (getApiKey().isBlank()) {
                statusText.text = "状态：请先输入 API Key"
            } else {
                fetchPrice()
            }
        }

        addButton(saveRow, "刷新报价") {
            fetchPrice()
        }

        settings.addView(saveRow)
        root.addView(settings)

        // Status
        val statusCard = makeCard()
        addLabel(statusCard, "连接状态", 14f, muted, true)

        statusText = addLabel(
            statusCard,
            "状态：等待 API Key",
            16f,
            gold,
            true
        )

        addLabel(
            statusCard,
            "自动查询间隔：10 分钟。每次查询都会消耗 API 请求额度。",
            11f
        )

        val pollRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        addButton(pollRow, "开始定时更新", true) {
            if (getApiKey().isBlank()) {
                statusText.text = "状态：请先保存 API Key"
            } else if (!polling) {
                polling = true
                fetchPrice()
                handler.postDelayed(pollRunnable, pollInterval)
                statusText.text = "状态：已启动定时查询"
            }
        }

        addButton(pollRow, "停止更新") {
            polling = false
            handler.removeCallbacks(pollRunnable)
            statusText.text = "状态：已停止定时查询"
        }

        statusCard.addView(pollRow)
        root.addView(statusCard)

        // Quote
        val quote = makeCard()
        addLabel(quote, "XAU / USD", 14f, muted, true)

        priceText = addLabel(
            quote,
            "等待真实报价",
            31f,
            gold,
            true
        )

        bidAskText = addLabel(
            quote,
            "Bid：—       Ask：—",
            14f,
            white,
            true
        )

        addLabel(quote, "OHLC · 最近完成的 K 线", 12f, muted, true)

        candleText = addLabel(
            quote,
            "Open：—\nHigh：—\nLow：—\nClose：—",
            13f,
            white
        )

        updateText = addLabel(
            quote,
            "数据时间：尚未获取",
            11f,
            muted
        )

        root.addView(quote)

        // Analysis status
        val analysis = makeCard()
        addLabel(analysis, "技术分析", 17f, white, true)
        addLabel(
            analysis,
            "EMA 9 / 20 / 50 / 200：等待历史 K 线",
            12f
        )
        addLabel(analysis, "RSI / MACD / ADX / ATR：待接入", 12f)
        addLabel(analysis, "支撑 / 阻力：待计算", 12f)
        root.addView(analysis)

        // Trade decision
        val decision = makeCard()
        addLabel(decision, "交易决策", 17f, white, true)
        addLabel(decision, "NO TRADE", 25f, red, true)
        addLabel(
            decision,
            "仅获取到报价并不代表交易信号成立。历史数据、指标和风险验证完成前，不生成买卖建议。",
            12f
        )
        addLabel(
            decision,
            "不自动下单。真实交易仍由你在 MT5 中决定。",
            12f,
            gold
        )
        root.addView(decision)

        modeText = addLabel(
            root,
            "版本：REST 行情连接测试",
            11f,
            muted
        )

        addLabel(
            root,
            "注意：API Key 保存在本机应用设置中，但 Android 本地存储不是专业密钥保险库。不要把密钥提交到 GitHub。",
            10f,
            muted
        )
    }

    private fun getApiKey(): String {
        val typed = apiKeyInput.text.toString().trim()
        if (typed.isNotBlank()) return typed

        return EncryptedApiKeyStore.get(this, "realmarket_api_key")
    }

    private fun fetchPrice() {
        val key = getApiKey()

        if (key.isBlank()) {
            statusText.text = "状态：请先输入 API Key"
            return
        }

        if (requestInProgress) {
            statusText.text = "状态：正在请求行情"
            return
        }

        requestInProgress = true
        statusText.text = "状态：正在连接 RealMarketAPI…"

        client.fetchPrice(
            apiKey = key,
            symbol = "XAUUSD",
            timeframe = "M1"
        ) { result ->

            requestInProgress = false

            result.onSuccess { quote ->
                showPrice(quote)
            }.onFailure { error ->
                statusText.text = "状态：请求失败"
                priceText.text = "暂无有效报价"
                updateText.text =
                    error.message ?: "未知错误"
            }
        }
    }

    private fun showPrice(q: RealMarketPrice) {
        statusText.text = "状态：已取得行情响应"
        statusText.setTextColor(green)

        priceText.text = String.format(
            Locale.US,
            "%.2f",
            q.close
        )

        val spread = q.spread?.let {
            String.format(Locale.US, "%.3f", it)
        } ?: "—"

        bidAskText.text = String.format(
            Locale.US,
            "Bid：%s       Ask：%s       Spread：%s",
            q.bid?.let { String.format(Locale.US, "%.3f", it) } ?: "—",
            q.ask?.let { String.format(Locale.US, "%.3f", it) } ?: "—",
            spread
        )

        candleText.text = String.format(
            Locale.US,
            "Open：%.3f\nHigh：%.3f\nLow：%.3f\nClose：%.3f\nVolume：%s",
            q.open,
            q.high,
            q.low,
            q.close,
            q.volume?.toString() ?: "—"
        )

        updateText.text =
            "K 线时间（UTC）：${q.openTime}\n" +
            "本机获取时间：${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())
            }"

        modeText.text =
            "已取得 ${q.symbol} 的接口数据。请核对报价与 K 线时间。"
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacks(pollRunnable)
        super.onDestroy()
    }
}
