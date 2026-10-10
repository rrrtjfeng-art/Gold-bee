package com.goldbee

import android.graphics.Color
import android.content.ComponentName
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.goldbee.analysis.MultiTimeframeAnalyzer
import com.goldbee.analysis.SwingSupportResistanceAnalyzer
import com.goldbee.analysis.StrategyBacktester
import com.goldbee.analysis.TakeProfitPlanner
import com.goldbee.analysis.TakeProfitStyle
import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.Trend
import com.goldbee.decision.CopySignalEvaluator
import com.goldbee.decision.CopySignalParser
import com.goldbee.decision.DecisionAction
import com.goldbee.decision.DecisionResult
import com.goldbee.decision.TradeDecisionGate
import com.goldbee.market.Candle
import com.goldbee.market.HistoricalCandleProvider
import com.goldbee.market.GoldPriceDevWebSocketClient
import com.goldbee.market.MarketFeedController
import com.goldbee.market.MarketTick
import com.goldbee.market.MarketTickListener
import com.goldbee.market.MarketSnapshot
import com.goldbee.market.RealMarketPrice
import com.goldbee.market.RealMarketRestClient
import com.goldbee.market.RealMarketPlanPolicy
import com.goldbee.market.RealMarketApiPlan
import com.goldbee.market.SourceQuoteTimestamp
import com.goldbee.market.Timeframe
import com.goldbee.mt5.Mt5ScreenAccessibilityService
import com.goldbee.settings.ApiKeyStore
import com.goldbee.settings.EncryptedApiKeyStore
import com.goldbee.risk.RiskStateStore
import com.goldbee.decision.TradeDirection
import com.goldbee.paper.PaperExitReason
import com.goldbee.paper.PaperQuote
import com.goldbee.paper.PaperSignal
import com.goldbee.paper.PaperTrade
import com.goldbee.paper.PaperTradeStatus
import com.goldbee.paper.PaperTradingEngine
import com.goldbee.paper.PaperTradeHistoryStore
import com.goldbee.paper.PaperTradePerformance
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(9, 12, 18)
    private val panel = Color.rgb(19, 24, 34)
    private val gold = Color.rgb(255, 193, 7)
    private val white = Color.rgb(240, 243, 250)
    private val muted = Color.rgb(145, 155, 173)
    private val green = Color.rgb(58, 210, 145)
    private val red = Color.rgb(255, 103, 112)
    private val client = RealMarketRestClient()
    // This build is configured for the user-confirmed RealMarketAPI Free plan.
    private val realMarketApiPlan = RealMarketApiPlan.FREE
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("gold_bee_settings", MODE_PRIVATE) }

    private lateinit var apiKeyInput: EditText
    private lateinit var goldPriceKeyInput: EditText
    private lateinit var liveFeedStatusText: TextView
    private lateinit var liveFeedQuoteText: TextView
    private lateinit var liveFeedAnalysisText: TextView
    @Volatile private var lastLiveAnalysisQueuedAt: Long = 0L
    @Volatile private var lastLiveTickReceivedAt: Long = 0L
    private var liveFeedClient: GoldPriceDevWebSocketClient? = null
    private val liveFeedController = MarketFeedController()
    private val mt5QuoteController = MarketFeedController()
    private val liveFeedListener = object : MarketTickListener {
        override fun onTick(tick: MarketTick) {
            val accepted = liveFeedController.submitTick(tick)
            if (accepted) lastLiveTickReceivedAt = System.currentTimeMillis()
            handler.post {
                if (!::liveFeedQuoteText.isInitialized) return@post
                liveFeedQuoteText.text = if (accepted) {
                    String.format(Locale.US, "XAUUSD Bid %.2f · Ask %.2f · Spread %.2f", tick.bid, tick.ask, tick.spread) +
                        "\n源时间：" + SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(tick.timestamp))
                } else {
                    "收到报价，但未通过行情新鲜度或品种检查；不可用于进场。"
                }
                liveFeedQuoteText.setTextColor(if (accepted) green else gold)
                if (accepted && liveFeedClient?.isConnected() == true && ::liveFeedStatusText.isInitialized) {
                    liveFeedStatusText.text = "状态：实时订阅正常 · 最新报价刚刚收到"
                    liveFeedStatusText.setTextColor(green)
                }
            }
            if (accepted) refreshLiveFeedAnalysis(tick)
        }

        override fun onConnected(source: String) {
            lastLiveTickReceivedAt = 0L
            handler.post {
                if (::liveFeedStatusText.isInitialized) {
                    liveFeedStatusText.text =
                        "状态：服务器已确认订阅 · 等待第一笔有效报价 · $source"
                    liveFeedStatusText.setTextColor(gold)
                }
            }
        }

        override fun onDisconnected(source: String) {
            handler.post {
                if (::liveFeedStatusText.isInitialized) {
                    liveFeedStatusText.text = "状态：实时行情已断开 · $source"
                    liveFeedStatusText.setTextColor(gold)
                }
            }
        }

        override fun onError(source: String, message: String) {
            handler.post {
                if (::liveFeedStatusText.isInitialized) {
                    liveFeedStatusText.text = "行情错误：$message"
                    liveFeedStatusText.setTextColor(red)
                }
            }
        }
    }
    private lateinit var twelveDataKeyInput: EditText
    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var bidAskText: TextView
    private lateinit var candleText: TextView
    private lateinit var updateText: TextView
    private lateinit var analysisText: TextView
    private lateinit var backtestText: TextView
    private lateinit var backtestCostInput: EditText
    private lateinit var decisionText: TextView
    private lateinit var decisionReasonText: TextView
    private lateinit var paperTradeText: TextView
    private lateinit var paperTradeHistoryText: TextView
    private lateinit var paperTpStatusText: TextView
    private lateinit var paperTpSmallButton: Button
    private lateinit var paperTpMediumButton: Button
    private lateinit var paperTpLargeButton: Button
    private lateinit var paperBalanceInput: EditText
    private lateinit var paperLotSizeInput: EditText
    private lateinit var paperContractSizeInput: EditText
    private lateinit var paperCommissionInput: EditText
    private var paperAccountCurrency: String = "USD"
    private var selectedPaperTpStyle: TakeProfitStyle = TakeProfitStyle.SMALL
    private var pendingRealSignalBase: PaperSignal? = null
    private var pendingPaperSignal: PaperSignal? = null
    private lateinit var copySignalInput: EditText
    private lateinit var copyResultText: TextView
    private lateinit var riskLossInput: EditText
    private lateinit var riskStatusText: TextView
    private lateinit var mt5ObservationText: TextView
    @Volatile private var latestCandles: Map<Timeframe, List<Candle>> = emptyMap()
    @Volatile private var latestAnalysis: MultiTimeframeAnalysis? = null
    @Volatile private var lastQuotePrice: Double? = null
    @Volatile private var latestQuote: RealMarketPrice? = null
    @Volatile private var lastQuoteReceivedAt: Long = 0L
    @Volatile private var lastQuoteSourceTimestamp: Long? = null
    private var polling = false
    private val observerRefreshRunnable = object : Runnable {
        override fun run() {
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
            refreshMt5Observation()
            refreshPaperTradeStatus()
            handler.postDelayed(this, OBSERVER_REFRESH_INTERVAL_MS)
        }
    }

    /**
     * A live socket can remain open while its last market tick is stale.
     * Keep transport status separate from quote freshness in the UI.
     */
    private val liveFeedHealthRunnable = object : Runnable {
        override fun run() {
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return

            if (::liveFeedStatusText.isInitialized && liveFeedClient?.isConnected() == true) {
                val lastTickAt = lastLiveTickReceivedAt
                if (lastTickAt <= 0L) {
                    liveFeedStatusText.text =
                        "状态：订阅已确认，但尚未收到有效报价；不能把连接状态当成实时价格。"
                    liveFeedStatusText.setTextColor(gold)
                } else {
                    val ageMillis = (System.currentTimeMillis() - lastTickAt).coerceAtLeast(0L)
                    if (ageMillis > LIVE_TICK_MAX_AGE_MS) {
                        liveFeedStatusText.text =
                            "状态：连接仍存在，但报价已过期（${ageMillis / 1000} 秒）；旧报价不可用于当前分析。"
                        liveFeedStatusText.setTextColor(red)
                    } else {
                        liveFeedStatusText.text =
                            "状态：实时订阅正常 · 最近报价 ${ageMillis} 毫秒前收到"
                        liveFeedStatusText.setTextColor(green)
                    }
                }
            }

            handler.postDelayed(this, LIVE_FEED_HEALTH_INTERVAL_MS)
        }
    }
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

    @Deprecated("Deprecated in Android API; retained for compatibility with the project target")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SCREEN_CAPTURE_REQUEST_CODE) return
        if (resultCode != RESULT_OK || data == null) {
            if (::mt5ObservationText.isInitialized) {
                mt5ObservationText.text = "屏幕 OCR 未获授权。没有开始捕获，也没有保存屏幕内容。"
                mt5ObservationText.setTextColor(gold)
            }
            return
        }
        val serviceIntent = android.content.Intent(this, com.goldbee.mt5.Mt5ScreenCaptureService::class.java)
            .putExtra(com.goldbee.mt5.Mt5ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(com.goldbee.mt5.Mt5ScreenCaptureService.EXTRA_RESULT_DATA, data)
        ContextCompat.startForegroundService(this, serviceIntent)
        if (::mt5ObservationText.isInitialized) {
            mt5ObservationText.text = "屏幕 OCR 已获授权，正在启动本机文字识别。请切换到 MT5 图表；要停止时返回 Gold Bee 并点击“停止屏幕 OCR”。"
            mt5ObservationText.setTextColor(green)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(observerRefreshRunnable)
        handler.post(observerRefreshRunnable)
        handler.removeCallbacks(liveFeedHealthRunnable)
        handler.post(liveFeedHealthRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(observerRefreshRunnable)
        handler.removeCallbacks(liveFeedHealthRunnable)
        super.onPause()
    }

    private fun refreshMt5Observation() {
        if (!::mt5ObservationText.isInitialized) return
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val serviceEnabled = enabledServices.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened)?.className ==
                Mt5ScreenAccessibilityService::class.java.name
        }
        val observation = getSharedPreferences(
            Mt5ScreenAccessibilityService.PREFS_NAME,
            MODE_PRIVATE
        )
        val observedAt = observation.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        val sourceRaw = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val ocrStatus = observation.getString(
            com.goldbee.mt5.Mt5ScreenCaptureService.KEY_OCR_STATUS,
            ""
        ).orEmpty()
        if (observedAt <= 0L) {
            val base = if (serviceEnabled) {
                "状态：无障碍权限已开启；切换到官方 MT5 并停留几秒，再返回 Gold Bee。"
            } else {
                "状态：无障碍权限未开启。可启用无障碍文字读取，或单独使用下方的屏幕 OCR。"
            }
            mt5ObservationText.text = if (ocrStatus.isBlank()) base else "$base\n$ocrStatus"
            mt5ObservationText.setTextColor(gold)
            return
        }
        if (!serviceEnabled && sourceRaw != "SCREEN_OCR") {
            mt5ObservationText.text =
                "最近一次记录来自无障碍读取，但权限当前未开启。你仍可单独启动屏幕 OCR。"
            mt5ObservationText.setTextColor(gold)
            return
        }

        fun value(key: String): String =
            observation.getString(key, "").orEmpty().ifBlank { "未识别" }
        fun price(key: String): String {
            val raw = observation.getString(key, "").orEmpty()
            return raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { String.format(Locale.US, "%.3f", it) } ?: "未识别"
        }
        val ageSeconds = ((System.currentTimeMillis() - observedAt).coerceAtLeast(0L)) / 1000L
        val source = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
            .let { if (it == "SCREEN_OCR") "屏幕 OCR" else "无障碍文字读取" }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(observedAt))
        mt5ObservationText.text = buildString {
            appendLine("状态：已读取到屏幕文字（只读观察）· 来源：$source")
            if (ocrStatus.isNotBlank()) appendLine("OCR 状态：$ocrStatus")
            appendLine("品种：${value(Mt5ScreenAccessibilityService.KEY_SYMBOL)} · 周期：${value(Mt5ScreenAccessibilityService.KEY_TIMEFRAME)}")
            appendLine("方向：${value(Mt5ScreenAccessibilityService.KEY_DIRECTION)}")
            appendLine("Entry：${price(Mt5ScreenAccessibilityService.KEY_ENTRY)} · SL：${price(Mt5ScreenAccessibilityService.KEY_SL)} · TP：${price(Mt5ScreenAccessibilityService.KEY_TP)}")
            appendLine("Bid：${price(Mt5ScreenAccessibilityService.KEY_BID)} · Ask：${price(Mt5ScreenAccessibilityService.KEY_ASK)}")
            appendLine("读取时间：$time（$ageSeconds 秒前）")
            append(if (ageSeconds <= 3L) "观察状态：刚刚更新；仍需核对识别内容。" else "观察状态：记录已过期，不能当作当前报价。")
            appendLine()
            append("安全限制：屏幕识别结果不是经验证的实时行情，不会单独触发交易信号。")
        }
        mt5ObservationText.setTextColor(if (ageSeconds <= 10L) green else gold)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun makeCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        setBackgroundColor(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun addLabel(
        parent: LinearLayout,
        value: String,
        size: Float = 13f,
        color: Int = muted,
        bold: Boolean = false
    ): TextView {
        val view = makeText(value, size, color, bold)
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        return view
    }

    private fun addButton(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            setTextColor(if (primary) bg else white)
            setBackgroundColor(if (primary) gold else Color.rgb(39, 46, 59))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        return button
    }

    private fun makeNumericInput(value: String, hintText: String): EditText =
        EditText(this).apply {
            setText(value)
            hint = hintText
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(31, 37, 49))
        }

    private fun setPaperCurrency(currency: String) {
        val nextCurrency = if (currency == "USC") "USC" else "USD"
        val previousCurrency = paperAccountCurrency
        if (nextCurrency != previousCurrency && ::paperBalanceInput.isInitialized) {
            val currentBalance = paperBalanceInput.text.toString().trim().toDoubleOrNull()
            if (currentBalance != null && currentBalance.isFinite() && currentBalance > 0.0) {
                val converted = if (nextCurrency == "USC") currentBalance * 100.0 else currentBalance / 100.0
                paperBalanceInput.setText(String.format(Locale.US, "%.2f", converted))
                prefs.edit().putString("paper_start_balance", converted.toString()).apply()
            }
        }
        paperAccountCurrency = nextCurrency
        prefs.edit().putString("paper_account_currency", paperAccountCurrency).apply()
        if (::paperTradeText.isInitialized) refreshPaperTradeStatus()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(22))
        }
        scroll.addView(root)
        setContentView(scroll)

        addLabel(root, "GOLD BEE", 27f, gold, true)
        addLabel(root, "XAUUSD · MARKET ANALYSIS TERMINAL", 11f)
        addLabel(root, "真实行情与历史技术分析测试版", 13f, white)

        val settings = makeCard()
        addLabel(settings, "行情连接设置", 17f, white, true)
        addLabel(settings, "参考行情：RealMarketAPI Free API Key（非实时）", 12f)
        apiKeyInput = makeSecretInput("输入 RealMarketAPI API Key")
        settings.addView(apiKeyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })

        val legacyKey = prefs.getString("api_key", "").orEmpty()
        if (legacyKey.isNotBlank()) {
            EncryptedApiKeyStore.save(this, "realmarket_api_key", legacyKey)
            prefs.edit().remove("api_key").apply()
        }
        if (EncryptedApiKeyStore.get(this, "realmarket_api_key").isNotBlank()) {
            apiKeyInput.hint = "参考行情 Key 已安全保存；留空继续使用"
        }

        addLabel(settings, "历史 K 线：Twelve Data API Key", 12f)
        twelveDataKeyInput = makeSecretInput("输入 Twelve Data API Key")
        settings.addView(twelveDataKeyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        if (ApiKeyStore.get(this).isNotBlank()) {
            twelveDataKeyInput.hint = "历史行情 Key 已安全保存；留空继续使用"
        }

        val saveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(saveRow, "保存行情 Key", true) {
            val realKey = apiKeyInput.text.toString().trim()
            if (realKey.isNotBlank()) {
                EncryptedApiKeyStore.save(this, "realmarket_api_key", realKey)
                prefs.edit().remove("api_key").apply()
            }
            val historyKey = twelveDataKeyInput.text.toString().trim()
            if (historyKey.isNotBlank()) ApiKeyStore.save(this, historyKey)
            statusText.text = when {
                getRealMarketKey().isBlank() -> "状态：请填写 RealMarketAPI Key"
                ApiKeyStore.get(this).isBlank() -> "状态：参考行情 Key 已保存；还需 Twelve Data Key 才能分析历史数据"
                else -> "状态：API Key 已安全保存"
            }
        }
        addButton(saveRow, "刷新参考数据") { fetchPrice() }
        settings.addView(saveRow)
        root.addView(settings)

        val liveFeedCard = makeCard()
        addLabel(liveFeedCard, "实时黄金行情连接（可选）", 17f, white, true)
        addLabel(
            liveFeedCard,
            "使用 GoldPrice.dev WebSocket。连续实时流需要供应商允许的套餐与 API Key；不要把延迟 REST 报价当成实时价格。此连接只接收行情，不自动下单，也不会单独产生 BUY/SELL 建议。",
            11f,
            muted
        )
        goldPriceKeyInput = makeSecretInput("输入 GoldPrice.dev API Key")
        if (EncryptedApiKeyStore.get(this, "goldprice_dev_api_key").isNotBlank()) {
            goldPriceKeyInput.hint = "GoldPrice.dev Key 已安全保存；留空继续使用"
        }
        liveFeedCard.addView(goldPriceKeyInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        liveFeedStatusText = addLabel(liveFeedCard, "状态：尚未连接实时行情", 12f, gold)
        liveFeedQuoteText = addLabel(liveFeedCard, "Bid：— · Ask：— · Spread：—", 13f, white)
        liveFeedAnalysisText = addLabel(
            liveFeedCard,
            "实时结构分析：等待有效行情与历史 K 线。请先加载历史数据并分析。",
            12f,
            muted
        )
        val liveFeedButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(liveFeedButtons, "保存实时行情 Key") {
            val entered = goldPriceKeyInput.text.toString().trim()
            if (entered.isNotBlank()) {
                EncryptedApiKeyStore.save(this, "goldprice_dev_api_key", entered)
                goldPriceKeyInput.text.clear()
                goldPriceKeyInput.hint = "实时行情 Key 已安全保存；留空继续使用"
                liveFeedStatusText.text = "状态：Key 已加密保存。请确认该 Key 已开通实时流权限后连接。"
                liveFeedStatusText.setTextColor(gold)
            } else {
                liveFeedStatusText.text = "请先输入 API Key；空白内容不会覆盖已保存的 Key。"
                liveFeedStatusText.setTextColor(red)
            }
        }
        addButton(liveFeedButtons, "连接实时行情", true) {
            val savedKey = EncryptedApiKeyStore.get(this, "goldprice_dev_api_key")
            if (savedKey.isBlank()) {
                liveFeedStatusText.text = "状态：请先保存 GoldPrice.dev API Key。"
                liveFeedStatusText.setTextColor(red)
            } else if (liveFeedClient == null) {
                liveFeedStatusText.text = "状态：正在连接；等待服务器确认订阅。"
                liveFeedStatusText.setTextColor(gold)
                liveFeedClient = GoldPriceDevWebSocketClient(savedKey, liveFeedListener)
                if (liveFeedClient?.connect() != true) liveFeedClient = null
            } else if (liveFeedClient?.isConnected() == true) {
                liveFeedStatusText.text = "状态：实时行情订阅已确认；正在接收或等待下一条行情。"
                liveFeedStatusText.setTextColor(green)
            } else {
                val retryStarted = liveFeedClient?.connect() == true
                liveFeedStatusText.text = if (retryStarted) {
                    "状态：重新连接请求已启动；等待服务器确认订阅。"
                } else {
                    "状态：无法启动连接；请检查网络、API Key 和套餐权限。"
                }
                liveFeedStatusText.setTextColor(if (retryStarted) gold else red)
            }
        }
        addButton(liveFeedButtons, "断开") {
            liveFeedClient?.disconnect()
            liveFeedClient = null
            liveFeedStatusText.text = "状态：已由你断开实时行情连接。"
            liveFeedStatusText.setTextColor(muted)
        }
        liveFeedCard.addView(liveFeedButtons)
        root.addView(liveFeedCard)

        val statusCard = makeCard()
        addLabel(statusCard, "连接状态", 14f, muted, true)
        statusText = addLabel(statusCard, "状态：等待 API Key", 16f, gold, true)
        addLabel(statusCard, "自动参考数据间隔：10 分钟。Free REST 数据不是连续实时行情，不用于短线进场；历史分析会额外消耗 Twelve Data 请求额度。", 11f)
        val pollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(pollRow, "开始定时更新参考数据", true) {
            if (getRealMarketKey().isBlank()) {
                statusText.text = "状态：请先保存 RealMarketAPI Key"
            } else if (!polling) {
                polling = true
                fetchPrice()
                handler.postDelayed(pollRunnable, pollInterval)
                statusText.text = "状态：已启动定时参考数据查询（非实时）"
            }
        }
        addButton(pollRow, "停止更新") {
            polling = false
            handler.removeCallbacks(pollRunnable)
            statusText.text = "状态：已停止定时查询"
        }
        statusCard.addView(pollRow)
        root.addView(statusCard)

        val quote = makeCard()
        addLabel(quote, "XAU / USD · REST参考数据（非实时）", 14f, muted, true)
        priceText = addLabel(quote, "等待参考数据", 31f, gold, true)
        bidAskText = addLabel(quote, "Bid：—       Ask：—", 14f, white, true)
        addLabel(quote, "OHLC · 最近返回的 K 线", 12f, muted, true)
        candleText = addLabel(quote, "Open：—\nHigh：—\nLow：—\nClose：—", 13f, white)
        updateText = addLabel(quote, "数据时间：尚未获取", 11f, muted)
        root.addView(quote)

        val mt5Card = makeCard()
        addLabel(mt5Card, "MT5 屏幕观察（只读）", 17f, white, true)
        addLabel(
            mt5Card,
            "只在官方 MT5 处于前台时读取系统可访问的文字，提取品种、周期、方向及明确标注的 Entry/SL/TP。不会点击或下单；图表上的线条、蜡烛图和非文本标签可能无法读取。",
            11f,
            muted
        )
        mt5ObservationText = addLabel(
            mt5Card,
            "尚未读取 MT5 屏幕。启用权限后切换到 MT5，再返回这里查看观察结果。",
            13f,
            white
        )
        val mt5Row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(mt5Row, "开启屏幕读取权限", true) {
            startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton(mt5Row, "刷新读取结果") { refreshMt5Observation() }
        mt5Card.addView(mt5Row)
        val mt5QuoteRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(mt5QuoteRow, "确认 MT5 报价并分析", true) { evaluateMt5ObservedQuote() }
        mt5Card.addView(mt5QuoteRow)
        addLabel(
            mt5Card,
            "只有你确认后才会使用屏幕读取的 Bid/Ask。识别过期、品种不明、历史数据过旧或报价与历史价格差距过大时，应用会拒绝生成进场方案。",
            10f,
            muted
        )
        val ocrRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(ocrRow, "开始屏幕 OCR", true) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
        }
        addButton(ocrRow, "停止屏幕 OCR") {
            stopService(
                android.content.Intent(this, com.goldbee.mt5.Mt5ScreenCaptureService::class.java)
                    .setAction(com.goldbee.mt5.Mt5ScreenCaptureService.ACTION_STOP)
            )
            getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(com.goldbee.mt5.Mt5ScreenCaptureService.KEY_OCR_STATUS, "屏幕 OCR 已停止。")
                .apply()
            refreshMt5Observation()
        }
        mt5Card.addView(ocrRow)
        addLabel(
            mt5Card,
            "屏幕 OCR 需要每次由你确认 Android 系统的屏幕共享提示。优先选择 MT5 应用窗口；识别只在本机处理，不保存截图或原始文字。",
            10f,
            muted
        )
        root.addView(mt5Card)
        refreshMt5Observation()

        val analysis = makeCard()
        addLabel(analysis, "多周期技术分析", 17f, white, true)
        analysisText = addLabel(
            analysis,
            "尚未加载历史 K 线。加载 M5、M15、H1 后计算 EMA、RSI、MACD、ADX、ATR 与支撑阻力。",
            12f,
            white
        )
        addLabel(
            analysis,
            "回测成本假设（XAUUSD 价格美元/每笔往返）：把点差、滑点及手续费折算成价格距离。0.00 表示完全未计成本，结果会偏乐观；请尽量按你的 MT5 实际成本填写。",
            11f,
            muted
        )
        backtestCostInput = EditText(this).apply {
            setText("0.00")
            hint = "例如 0.30；0 = 未计成本"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(31, 37, 49))
        }
        analysis.addView(
            backtestCostInput,
            LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) }
        )
        addButton(analysis, "加载历史数据并分析", true) { loadHistoricalAnalysis() }
        addButton(analysis, "回测当前策略") { runStrategyBacktest() }
        backtestText = addLabel(
            analysis,
            "尚未回测。可设置往返成本假设，并同时查看扣成本前后的结果；回测仍不等于实盘预测。",
            12f,
            muted
        )
        root.addView(analysis)

        val decision = makeCard()
        addLabel(decision, "研究信号（未回测）", 17f, white, true)
        decisionText = addLabel(decision, "NO TRADE", 25f, red, true)
        decisionReasonText = addLabel(decision, "先加载 M5/M15/H1 历史 K 线，再用 MT5 屏幕当前 Bid/Ask 生成 REAL 判断。参考行情不会单独作为可执行信号。", 12f, white)
        addLabel(
            decision,
            "注意：信号逻辑尚未证明具有盈利优势。仅用于研究，不代表盈利保证；不自动下单，真实交易由你在 MT5 中决定。",
            11f,
            gold
        )
        addButton(decision, "用 MT5 当前报价分析 REAL 信号", true) { analyzeRealFromMt5Screen() }
        addLabel(decision, "REAL 止盈目标（XAUUSD 价格距离，不是保证收益）", 13f, white, true)
        val tpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        paperTpSmallButton = addButton(tpRow, "小赚 +2.00", selectedPaperTpStyle == TakeProfitStyle.SMALL) {
            selectPaperTpStyle(TakeProfitStyle.SMALL)
        }
        paperTpMediumButton = addButton(tpRow, "中赚 +5.00", selectedPaperTpStyle == TakeProfitStyle.MEDIUM) {
            selectPaperTpStyle(TakeProfitStyle.MEDIUM)
        }
        paperTpLargeButton = addButton(tpRow, "大赚 +10.00", selectedPaperTpStyle == TakeProfitStyle.LARGE) {
            selectPaperTpStyle(TakeProfitStyle.LARGE)
        }
        decision.addView(tpRow)
        paperTpStatusText = addLabel(
            decision,
            "默认选择小赚 TP +2.00。只有按实际 MT5 Bid/Ask 进场价并计入往返佣金后净盈亏比达到 1.0，才允许确认模拟单。",
            11f,
            muted
        )
        addLabel(decision, "模拟账户参数", 14f, white, true)
        addLabel(
            decision,
            "合约规格因经纪商而异。100 盎司/手只是常见默认假设，请先核对 MT5 品种规格；佣金 0 表示暂未计佣金。点差按 MT5 屏幕 Bid/Ask 模拟。",
            11f,
            gold
        )
        paperBalanceInput = makeNumericInput(
            prefs.getString("paper_start_balance", "1000.00").orEmpty(),
            "初始余额（账户单位）"
        )
        decision.addView(paperBalanceInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        paperLotSizeInput = makeNumericInput(
            prefs.getString("paper_lot_size", "0.01").orEmpty(),
            "模拟手数，例如 0.01"
        )
        decision.addView(paperLotSizeInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        paperContractSizeInput = makeNumericInput(
            prefs.getString("paper_contract_ounces", "100").orEmpty(),
            "每手合约大小（盎司）"
        )
        decision.addView(paperContractSizeInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        paperCommissionInput = makeNumericInput(
            prefs.getString("paper_commission_round_turn", "0.00").orEmpty(),
            "每手往返佣金（USD）"
        )
        decision.addView(paperCommissionInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        paperAccountCurrency = prefs.getString("paper_account_currency", "USD").orEmpty().ifBlank { "USD" }
        val currencyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(currencyRow, "账户单位：USD", paperAccountCurrency == "USD") { setPaperCurrency("USD") }
        addButton(currencyRow, "账户单位：USC 美分", paperAccountCurrency == "USC") { setPaperCurrency("USC") }
        decision.addView(currencyRow)
        paperTradeText = addLabel(
            decision,
            "模拟账户：尚无交易。确认按钮只会创建本地模拟记录，不会点击 MT5 或发送真实订单。",
            12f,
            white
        )
        addLabel(decision, "模拟交易统计与最近记录", 13f, white, true)
        paperTradeHistoryText = addLabel(decision, "尚无已平仓模拟交易。", 11f, muted)
        val paperRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(paperRow, "确认信号并开模拟单", true) { confirmPaperTrade() }
        addButton(paperRow, "刷新报价并检查 SL/TP") { updatePaperTradeFromQuote() }
        decision.addView(paperRow)
        addButton(decision, "按当前报价手动平仓模拟单") { closePaperTradeManually() }
        root.addView(decision)
        refreshPaperTradeStatus()

        val riskCard = makeCard()
        addLabel(riskCard, "风险记录（手动同步 MT5 结果）", 17f, white, true)
        addLabel(
            riskCard,
            "应用无法读取 MT5 账户成交与盈亏。每笔结束后，请按账户余额百分比手动记录；没有记录的结果不会自动计入。记录仅用于本机的风险闸门。",
            11f,
            muted
        )
        riskStatusText = addLabel(riskCard, "", 13f, white)
        riskLossInput = EditText(this).apply {
            hint = "本笔亏损占账户百分比，例如 0.5"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(31, 37, 49))
        }
        riskCard.addView(
            riskLossInput,
            LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) }
        )
        val riskRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(riskRow, "记录亏损", true) {
            val loss = riskLossInput.text.toString().trim().toDoubleOrNull()
            if (loss == null || !loss.isFinite() || loss <= 0.0 || loss > 100.0) {
                riskStatusText.text = "输入无效：请输入大于 0 且不超过 100 的账户亏损百分比。"
                riskStatusText.setTextColor(red)
            } else {
                RiskStateStore.recordLoss(this, loss)
                riskLossInput.text.clear()
                refreshRiskStatus()
            }
        }
        addButton(riskRow, "记录盈利") {
            RiskStateStore.recordWin(this)
            refreshRiskStatus()
        }
        riskCard.addView(riskRow)
        root.addView(riskCard)
        refreshRiskStatus()

        val copyCard = makeCard()
        addLabel(copyCard, "COPY · 外部信号审核", 17f, white, true)
        addLabel(
            copyCard,
            "粘贴信号后，应用会检查方向、Entry、SL、TP、风险回报比、当前 MT5 屏幕报价、点差、价格距离和多周期趋势。没有新鲜且有效的 MT5 Bid/Ask 时，一律 NO TRADE。",
            11f,
            muted
        )
        copySignalInput = makeCopyInput()
        copyCard.addView(
            copySignalInput,
            LinearLayout.LayoutParams(-1, dp(112)).apply { bottomMargin = dp(8) }
        )
        val copyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addButton(copyRow, "审核复制信号", true) { evaluateCopySignal() }
        addButton(copyRow, "清空") {
            copySignalInput.text.clear()
            copyResultText.text = "等待审核。"
            copyResultText.setTextColor(white)
        }
        copyCard.addView(copyRow)
        copyResultText = addLabel(copyCard, "等待审核。先加载 M5、M15、H1 历史数据，并确保 MT5 屏幕读取或 OCR 正在更新。行情过期或识别不全时会拒绝跟随。", 13f, white)
        root.addView(copyCard)

        addLabel(root, "安全提示：API Key 使用 Android Keystore 加密后保存在本机。不要把密钥提交到 GitHub。", 10f, muted)
    }

    private fun makeSecretInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(muted)
        setTextColor(white)
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        maxLines = 1
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(31, 37, 49))
    }

    private fun makeCopyInput(): EditText = EditText(this).apply {
        hint = "例如：BUY Entry: 4050 SL: 4040 TP: 4070"
        setHintTextColor(muted)
        setTextColor(white)
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 3
        maxLines = 5
        gravity = android.view.Gravity.TOP
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(31, 37, 49))
    }

    private fun evaluateCopySignal() {
        pendingPaperSignal = null
        pendingRealSignalBase = null
        updatePendingRealTakeProfit()
        val parsed = CopySignalParser.parse(copySignalInput.text.toString())
        val signal = parsed.getOrNull()
        if (signal == null) {
            copyResultText.setTextColor(red)
            copyResultText.text = parsed.exceptionOrNull()?.message ?: "无法识别信号。"
            return
        }

        val entry = signal.entry
        val stopLoss = signal.stopLoss
        val takeProfit = signal.takeProfit
        if (entry == null || stopLoss == null || takeProfit == null) {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE：必须明确提供 Entry、SL 和 TP；缺少任何一项都禁止跟随。"
            return
        }
        val levelsValid = when (signal.direction.name) {
            "BUY" -> stopLoss < entry && takeProfit > entry
            "SELL" -> takeProfit < entry && stopLoss > entry
            else -> false
        }
        if (!levelsValid) {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE：止损和止盈位置与 BUY/SELL 方向不匹配。"
            return
        }

        val observation = getSharedPreferences(
            Mt5ScreenAccessibilityService.PREFS_NAME,
            MODE_PRIVATE
        )
        val observedAt = observation.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        val source = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val symbol = observation.getString(Mt5ScreenAccessibilityService.KEY_SYMBOL, "")
            .orEmpty().uppercase().replace("/", "")
        val bid = observation.getString(Mt5ScreenAccessibilityService.KEY_BID, "")
            .orEmpty().toDoubleOrNull()
        val ask = observation.getString(Mt5ScreenAccessibilityService.KEY_ASK, "")
            .orEmpty().toDoubleOrNull()
        val ageMillis = System.currentTimeMillis() - observedAt

        fun blockCopy(reason: String) {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE：$reason"
        }

        if (source != "ACCESSIBILITY" && source != "SCREEN_OCR") {
            blockCopy("没有来自 MT5 的屏幕行情。启用只读读取或屏幕 OCR 后重试。")
            return
        }
        if (observedAt <= 0L || ageMillis !in 0L..3000L) {
            blockCopy("MT5 屏幕报价已过期。请切换到 MT5 等待更新，再立即审核信号。")
            return
        }
        if (symbol !in setOf("XAUUSD", "GOLD")) {
            blockCopy("无法确认当前屏幕品种为 XAUUSD；拒绝使用不确定的报价。")
            return
        }
        if (bid == null || ask == null || !bid.isFinite() || !ask.isFinite() ||
            bid <= 0.0 || ask <= bid
        ) {
            blockCopy("当前 MT5 屏幕未识别到有效 Bid 与 Ask；不能用延迟 REST 报价代替。")
            return
        }

        val history = latestCandles
        val priorAnalysis = latestAnalysis
        val requiredTimeframes = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)
        if (priorAnalysis == null || requiredTimeframes.any { history[it].orEmpty().size < 50 }) {
            blockCopy("历史 K 线不足。先点击“加载历史数据并分析”。")
            return
        }
        val nowSeconds = System.currentTimeMillis() / 1000L
        val staleTimeframe = requiredTimeframes.firstOrNull { tf ->
            val last = history[tf].orEmpty().lastOrNull() ?: return@firstOrNull true
            val ageSeconds = nowSeconds - last.timestamp
            ageSeconds < -tf.seconds || ageSeconds > tf.seconds * 2L + 60L
        }
        if (staleTimeframe != null) {
            blockCopy(staleTimeframe.name + " 历史 K 线过旧；请重新加载。")
            return
        }

        val atr = priorAnalysis.m15.indicators.atr14
        if (atr == null || !atr.isFinite() || atr <= 0.0) {
            blockCopy("M15 ATR 不可用，无法检查点差与追价风险。")
            return
        }
        val currentPrice = bid + (ask - bid) / 2.0
        if (ask - bid > atr * 0.15) {
            blockCopy("当前 MT5 点差相对 M15 ATR 过大；等待点差收窄。")
            return
        }
        val latestM5Close = history[Timeframe.M5].orEmpty().last().close
        if (kotlin.math.abs(currentPrice - latestM5Close) > atr) {
            blockCopy("当前 MT5 价格与最近 M5 收盘价相差超过 1 个 M15 ATR；可能是数据源差异或行情跳变，禁止追价。")
            return
        }

        val mt5Tick = MarketTick(
            symbol = "XAUUSD",
            bid = bid,
            ask = ask,
            timestamp = observedAt,
            source = "MT5 screen quote (COPY review)"
        )
        val accepted = mt5QuoteController.submitTick(mt5Tick)
        val lastTick = mt5QuoteController.getLatestTick()
        if (!accepted && (lastTick == null ||
                lastTick.timestamp != observedAt ||
                lastTick.bid != bid ||
                lastTick.ask != ask)
        ) {
            blockCopy("MT5 报价未通过新鲜度或顺序验证；等待屏幕刷新后重试。")
            return
        }

        val candles = linkedMapOf(
            Timeframe.M5 to mt5QuoteController.getCandles(Timeframe.M5),
            Timeframe.M15 to mt5QuoteController.getCandles(Timeframe.M15),
            Timeframe.H1 to mt5QuoteController.getCandles(Timeframe.H1)
        )
        val analysis = try {
            MultiTimeframeAnalyzer.analyze(candles)
        } catch (error: Exception) {
            blockCopy("当前行情分析失败：" + (error.message ?: "未知错误"))
            return
        }
        latestCandles = candles
        latestAnalysis = analysis

        val signalRisk = kotlin.math.abs(entry - stopLoss)
        val signalReward = kotlin.math.abs(takeProfit - entry)
        if (signalRisk <= 0.0 || signalReward / signalRisk < 1.0) {
            blockCopy("信号的风险回报比低于 1:1；不跟随。")
            return
        }

        val evaluation = CopySignalEvaluator.evaluate(
            signal = signal,
            currentPrice = currentPrice,
            analysis = analysis,
            candles = candles,
            spread = ask - bid
        )
        if (evaluation.action.name == "NO_TRADE") {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE\n当前报价：${fmt(currentPrice)}\nEntry 距离：${fmt(evaluation.priceDistance)}\n原因：${evaluation.reason}"
            return
        }

        val risk = kotlin.math.abs(entry - stopLoss)
        val reward = kotlin.math.abs(takeProfit - entry)
        val rr = if (risk > 0.0) reward / risk else 0.0
        copyResultText.setTextColor(gold)
        copyResultText.text = String.format(
            Locale.US,
            "审核结果：%s（仅供人工复核）\n当前报价：%.3f\nEntry：%.3f · SL：%.3f · TP：%.3f\nR:R = 1:%.2f\n价格距离：%.3f\n理由：%s\n\n这不是下单指令。再次确认点差、报价和风险后，由你自行决定是否在 MT5 操作。",
            evaluation.action.name,
            currentPrice,
            entry,
            stopLoss,
            takeProfit,
            rr,
            evaluation.priceDistance ?: 0.0,
            evaluation.reason
        )
        copyResultText.text = copyResultText.text.toString() +
            "\n报价来源：MT5 屏幕读取（${ageMillis} ms）。历史 K 线来自 Twelve Data。" +
            "\n此结果只用于人工复核，不会自动下单，也不保证盈利。"
        pendingPaperSignal = PaperSignal(
            direction = signal.direction,
            plannedEntry = entry,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            source = "COPY",
            createdAtMillis = System.currentTimeMillis()
        )
        paperTradeText.text = "COPY 信号已通过审核。请确认后开模拟单；开仓时会重新读取 MT5 Bid/Ask，并再次检查报价新鲜度、追价距离和风险回报比。"
        paperTradeText.setTextColor(gold)
    }

    private fun refreshQuoteAndEvaluate(
        candles: Map<Timeframe, List<Candle>>,
        analysis: MultiTimeframeAnalysis
    ) {
        val key = getRealMarketKey()
        if (key.isBlank()) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：缺少 RealMarketAPI Free 参考数据 API Key。"
                )
            )
            return
        }

        client.fetchPrice(apiKey = key, symbol = "XAUUSD", timeframe = "M1") { result ->
            result.onSuccess { quote ->
                showPrice(quote)
                // Free REST returns candle snapshots, not a continuous live Bid/Ask stream.
                // Keep the technical analysis visible, but fail closed for actionable setups.
                if (!RealMarketPlanPolicy.allowsActionableSignals(realMarketApiPlan)) {
                    showDecision(
                        DecisionResult(
                            action = DecisionAction.NO_TRADE,
                            setup = null,
                            confidence = 0.0,
                            reason = RealMarketPlanPolicy.blockReason(realMarketApiPlan)
                        )
                    )
                    return@onSuccess
                }

                val bid = quote.bid
                val ask = quote.ask
                if (bid == null || ask == null ||
                    !SourceQuoteTimestamp.isFresh(lastQuoteSourceTimestamp)
                ) {
                    showDecision(
                        DecisionResult(
                            action = DecisionAction.NO_TRADE,
                            setup = null,
                            confidence = 0.0,
                            reason = "NO TRADE：Bid/Ask 缺失或行情来源时间无法验证/过旧。"
                        )
                    )
                    return@onSuccess
                }

                val receivedAt = lastQuoteReceivedAt
                val snapshot = MarketSnapshot(
                    symbol = quote.symbol,
                    bid = bid,
                    ask = ask,
                    timestamp = lastQuoteSourceTimestamp ?: 0L,
                    candles = candles,
                    source = "RealMarketAPI",
                    receivedAt = receivedAt
                )
                val gated = TradeDecisionGate.evaluate(
                    snapshot = snapshot,
                    analysis = analysis,
                    candles = candles,
                    riskState = RiskStateStore.get(this)
                )
                showDecision(gated.decision)
            }.onFailure { error ->
                showDecision(
                    DecisionResult(
                        action = DecisionAction.NO_TRADE,
                        setup = null,
                        confidence = 0.0,
                        reason = "NO TRADE：获取参考行情失败。Free 版数据不可替代实时行情。" + (error.message ?: "")
                    )
                )
            }
        }
    }

    private fun selectPaperTpStyle(style: TakeProfitStyle) {
        selectedPaperTpStyle = style
        refreshTpStyleButtons()
        updatePendingRealTakeProfit()
    }

    private fun refreshTpStyleButtons() {
        if (!::paperTpSmallButton.isInitialized) return
        val styles = listOf(
            paperTpSmallButton to TakeProfitStyle.SMALL,
            paperTpMediumButton to TakeProfitStyle.MEDIUM,
            paperTpLargeButton to TakeProfitStyle.LARGE
        )
        styles.forEach { (button, style) ->
            val selected = style == selectedPaperTpStyle
            button.setBackgroundColor(if (selected) gold else Color.rgb(39, 46, 59))
            button.setTextColor(if (selected) bg else white)
        }
    }

    private fun updatePendingRealTakeProfit() {
        if (!::paperTpStatusText.isInitialized) return
        val base = pendingRealSignalBase
        if (base == null || base.source != "REAL") {
            paperTpStatusText.text = "TP 风格只影响 REAL 模式；COPY 信号保留原始 TP。当前选择：${selectedPaperTpStyle.label} +${fmt(selectedPaperTpStyle.priceDistance)}。"
            paperTpStatusText.setTextColor(muted)
            return
        }
        val target = runCatching {
            TakeProfitPlanner.targets(base.direction, base.plannedEntry, base.stopLoss)
                .first { it.style == selectedPaperTpStyle }
        }.getOrNull()
        if (target == null) {
            pendingPaperSignal = null
            paperTpStatusText.text = "所选 TP 无法根据当前 Entry/SL 计算；不能开模拟单。"
            paperTpStatusText.setTextColor(red)
            return
        }
        val selectedSignal = base.copy(takeProfit = target.price)
        pendingPaperSignal = selectedSignal
        val quote = readFreshPaperQuote()
        val now = System.currentTimeMillis()
        if (quote == null || now - quote.timestampMillis !in 0L..PaperTradingEngine.MAX_QUOTE_AGE_MILLIS ||
            quote.ask <= quote.bid
        ) {
            paperTpStatusText.text = "已选择 ${target.style.label} TP ${fmt(target.price)}（距离 ${fmt(target.distance)}，约 ${fmt(target.estimatedPips)} pips）。当前 MT5 报价不够新，暂不能验证净盈亏比；确认时会再次拦截。"
            paperTpStatusText.setTextColor(gold)
            return
        }
        val entry = if (base.direction == TradeDirection.BUY) quote.ask else quote.bid
        val risk = kotlin.math.abs(entry - base.stopLoss)
        val reward = kotlin.math.abs(target.price - entry)
        val lotSize = paperLotSizeInput.text.toString().trim().toDoubleOrNull()
        val contractSize = paperContractSizeInput.text.toString().trim().toDoubleOrNull()
        val commission = paperCommissionInput.text.toString().trim().toDoubleOrNull()
        if (lotSize == null || !lotSize.isFinite() || lotSize <= 0.0 ||
            contractSize == null || !contractSize.isFinite() || contractSize <= 0.0 ||
            commission == null || !commission.isFinite() || commission < 0.0
        ) {
            pendingPaperSignal = null
            paperTpStatusText.text = "请先填写有效的模拟手数、每手合约大小和往返佣金，才能评估所选 TP。"
            paperTpStatusText.setTextColor(red)
            return
        }
        val netRewardMoney = reward * contractSize * lotSize - commission * lotSize
        val netRiskMoney = risk * contractSize * lotSize + commission * lotSize
        val netRr = if (netRiskMoney > 0.0) netRewardMoney / netRiskMoney else Double.NaN
        if (!risk.isFinite() || risk <= 0.0 || !netRr.isFinite() ||
            netRewardMoney <= 0.0 || netRiskMoney <= 0.0 || netRr < 1.0
        ) {
            pendingPaperSignal = null
            paperTpStatusText.text = "NO TRADE：${target.style.label} TP ${fmt(target.price)} 按实际 Bid/Ask 进场价及佣金计算后，净盈亏比不足 1.0（估算 ${fmt(netRr)}）。请改选更远 TP 或放弃交易。"
            paperTpStatusText.setTextColor(red)
            return
        }
        paperTpStatusText.text = "已选 ${target.style.label} TP：${fmt(target.price)} · 距离 ${fmt(target.distance)}（约 ${fmt(target.estimatedPips)} pips）· 按实际进场价及佣金估算净 R:R 1:${fmt(netRr)}。模拟开仓仍需再次通过价格和风险检查。"
        paperTpStatusText.setTextColor(green)
    }

    private fun analyzeRealFromMt5Screen() {
        pendingPaperSignal = null
        pendingRealSignalBase = null
        updatePendingRealTakeProfit()
        val now = System.currentTimeMillis()
        val observation = getSharedPreferences(
            Mt5ScreenAccessibilityService.PREFS_NAME,
            MODE_PRIVATE
        )
        val source = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val observedAt = observation.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        val age = now - observedAt
        if (source !in setOf("ACCESSIBILITY", "SCREEN_OCR")) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：没有 MT5 屏幕报价。请启用只读无障碍读取或用户授权的屏幕 OCR。"
                )
            )
            return
        }
        if (observedAt <= 0L || age !in 0L..PaperTradingEngine.MAX_QUOTE_AGE_MILLIS) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：MT5 屏幕报价观察已过期（${age.coerceAtLeast(0L)} ms）。切换到 MT5，等 Bid/Ask 更新后立即重试。"
                )
            )
            return
        }

        val quote = readFreshPaperQuote()
        if (quote == null || quote.ask <= quote.bid) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：无法可靠读取有效的 XAUUSD Bid/Ask；请核对 MT5 画面和 OCR 识别。"
                )
            )
            return
        }
        val normalizedSymbol = quote.symbol.uppercase().replace("/", "").trim()
        if (normalizedSymbol !in setOf("XAUUSD", "GOLD")) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：当前屏幕品种为 $normalizedSymbol，不是已确认的 XAUUSD/GOLD。"
                )
            )
            return
        }

        val nowSeconds = now / 1000L
        val candles = latestCandles.mapValues { (timeframe, items) ->
            items.filter { candle -> candle.timestamp + timeframe.seconds <= nowSeconds }
        }
        val minimumBars = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1).all { tf ->
            candles[tf].orEmpty().size >= 50
        }
        val analysis = if (minimumBars) runCatching {
            MultiTimeframeAnalyzer.analyze(candles)
        }.getOrNull() else null
        if (!minimumBars || analysis == null) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "NO TRADE：闭合 K 线不足。请先加载历史数据，并确保排除未收盘 K 线后 M5/M15/H1 每周期仍至少有 50 根有效 K 线。"
                )
            )
            return
        }

        val snapshot = MarketSnapshot(
            symbol = "XAUUSD",
            bid = quote.bid,
            ask = quote.ask,
            timestamp = quote.timestampMillis,
            candles = candles,
            source = "MT5_SCREEN",
            receivedAt = System.currentTimeMillis()
        )
        val gated = TradeDecisionGate.evaluate(
            snapshot = snapshot,
            analysis = analysis,
            candles = candles,
            riskState = RiskStateStore.get(this)
        )
        showDecision(gated.decision)
        decisionReasonText.text = decisionReasonText.text.toString() +
            "\n\n报价来源：MT5 屏幕读取（观察年龄 ${age} ms）" +
            "\nBid：${fmt(quote.bid)} · Ask：${fmt(quote.ask)} · 点差：${fmt(quote.ask - quote.bid)}" +
            "\n风险闸门：${gated.reason}" +
            "\n注意：这是根据屏幕报价与已加载历史 K 线计算的方案，不保证盈利；确认模拟单时会再次检查报价与追价距离。"
    }

    private fun showDecision(decision: DecisionResult) {
        if (decision.action == DecisionAction.BUY || decision.action == DecisionAction.SELL) {
            val setup = decision.setup
            pendingRealSignalBase = if (setup == null) null else PaperSignal(
                direction = if (decision.action == DecisionAction.BUY) TradeDirection.BUY else TradeDirection.SELL,
                plannedEntry = setup.entry,
                stopLoss = setup.stopLoss,
                takeProfit = setup.takeProfit,
                source = "REAL",
                createdAtMillis = System.currentTimeMillis()
            )
            pendingPaperSignal = pendingRealSignalBase
            updatePendingRealTakeProfit()
            if (pendingPaperSignal != null && ::paperTradeText.isInitialized) {
                paperTradeText.text = "REAL 信号已生成。当前 TP 风格需通过净盈亏比检查；确认后只开模拟单。"
                paperTradeText.setTextColor(gold)
            }
        } else {
            pendingRealSignalBase = null
            pendingPaperSignal = null
            updatePendingRealTakeProfit()
        }
        decisionText.text = decision.action.name
        decisionText.setTextColor(
            when (decision.action) {
                DecisionAction.BUY -> green
                DecisionAction.SELL, DecisionAction.NO_TRADE -> red
                else -> gold
            }
        )
        val setup = decision.setup
        decisionReasonText.text = if (setup == null) {
            decision.reason
        } else {
            val targetOptions = TakeProfitPlanner.targets(
                direction = setup.direction,
                entry = setup.entry,
                stopLoss = setup.stopLoss
            ).joinToString(separator = "\n") { target ->
                String.format(
                    Locale.US,
                    "%s TP：%.3f · 价格距离 %.2f 美元（约 %.0f pips）· R:R 1:%.2f%s",
                    target.style.label,
                    target.price,
                    target.distance,
                    target.estimatedPips,
                    target.riskReward,
                    if (target.riskReward < 1.0) " · 风险回报不足" else ""
                )
            }
            String.format(
                Locale.US,
                "%s\nEntry：%.3f\nSL：%.3f\n模型原始 TP：%.3f（R:R 1:%.1f）\n模型评分（非胜率）：%.0f%%\n注意：该分数不是历史胜率或盈利概率，未经独立回测验证不能代表未来成功概率。\n原因：%s\n\n备选 TP（固定价格距离：小赚 +2 美元、中赚 +5 美元、大赚 +10 美元；每个目标按实际 SL 计算 R:R）：\n%s\nPips 暂按 1 pip = 0.1 美元价格变化估算；不同 MT5 经纪商的点值定义可能不同。备选目标仅供比较，尚未单独通过进场闸门。",
                decision.reason,
                setup.entry,
                setup.stopLoss,
                setup.takeProfit,
                setup.riskReward,
                decision.confidence * 100.0,
                setup.reason,
                targetOptions
            )
        }
    }


    private fun readFreshPaperQuote(): PaperQuote? {
        val observation = getSharedPreferences(
            Mt5ScreenAccessibilityService.PREFS_NAME,
            MODE_PRIVATE
        )
        val source = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        if (source != "ACCESSIBILITY" && source != "SCREEN_OCR") return null
        return PaperQuote(
            symbol = observation.getString(Mt5ScreenAccessibilityService.KEY_SYMBOL, "").orEmpty(),
            bid = observation.getString(Mt5ScreenAccessibilityService.KEY_BID, "").orEmpty().toDoubleOrNull() ?: Double.NaN,
            ask = observation.getString(Mt5ScreenAccessibilityService.KEY_ASK, "").orEmpty().toDoubleOrNull() ?: Double.NaN,
            timestampMillis = observation.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        )
    }

    private fun confirmPaperTrade() {
        val now = System.currentTimeMillis()
        val active = loadPaperTrade()
        if (active?.status == PaperTradeStatus.OPEN) {
            paperTradeText.text = "已有模拟单持仓。请先刷新报价检查 SL/TP，或手动平仓；不能重复开仓。"
            paperTradeText.setTextColor(gold)
            return
        }
        val signal = pendingPaperSignal
        if (signal == null) {
            paperTradeText.text = "没有有效的 BUY/SELL 信号。先重新分析 MT5 报价，或通过 COPY 审核获得可用信号。"
            paperTradeText.setTextColor(red)
            return
        }
        if (now - signal.createdAtMillis !in 0L..120_000L) {
            pendingPaperSignal = null
            paperTradeText.text = "信号已超过 2 分钟，已拒绝使用旧信号。请重新分析后再确认。"
            paperTradeText.setTextColor(red)
            return
        }
        val analysis = latestAnalysis
        val atr = analysis?.m15?.indicators?.atr14
        if (atr == null || !atr.isFinite() || atr <= 0.0) {
            paperTradeText.text = "M15 ATR 不可用，无法验证进场偏差；不能开模拟单。"
            paperTradeText.setTextColor(red)
            return
        }
        val quote = readFreshPaperQuote()
        if (quote == null) {
            paperTradeText.text = "未取得 MT5 屏幕 Bid/Ask。请开启只读读取/OCR，确认 XAUUSD 报价可见后重试。"
            paperTradeText.setTextColor(red)
            return
        }
        val startingBalance = paperBalanceInput.text.toString().trim().toDoubleOrNull()
        val lotSize = paperLotSizeInput.text.toString().trim().toDoubleOrNull()
        val contractSize = paperContractSizeInput.text.toString().trim().toDoubleOrNull()
        val commission = paperCommissionInput.text.toString().trim().toDoubleOrNull()
        if (startingBalance == null || !startingBalance.isFinite() || startingBalance <= 0.0 ||
            lotSize == null || !lotSize.isFinite() || lotSize <= 0.0 ||
            contractSize == null || !contractSize.isFinite() || contractSize <= 0.0 ||
            commission == null || !commission.isFinite() || commission < 0.0
        ) {
            paperTradeText.text = "模拟账户参数无效。请检查初始余额、手数、每手盎司和往返佣金。"
            paperTradeText.setTextColor(red)
            return
        }
        prefs.edit()
            .putString("paper_start_balance", startingBalance.toString())
            .putString("paper_lot_size", lotSize.toString())
            .putString("paper_contract_ounces", contractSize.toString())
            .putString("paper_commission_round_turn", commission.toString())
            .putString("paper_account_currency", paperAccountCurrency)
            .apply()
        val attempt = PaperTradingEngine.open(
            signal = signal,
            quote = quote,
            nowMillis = now,
            maxEntryDistance = atr * 0.35,
            minimumRiskReward = 1.0,
            lotSize = lotSize,
            contractSizeOunces = contractSize,
            commissionPerLotRoundTurnUsd = commission
        )
        val trade = attempt.trade
        if (trade == null) {
            paperTradeText.text = "模拟开仓被拒绝：${attempt.reason}"
            paperTradeText.setTextColor(red)
            return
        }
        savePaperTrade(trade)
        prefs.edit().putString("paper_monitor_status", "等待新的 MT5 报价，自动检查 SL/TP。").apply()
        pendingPaperSignal = null
        paperTradeText.text = "模拟单已开启：${trade.direction} · Entry ${fmt(trade.entryPrice)} · SL ${fmt(trade.stopLoss)} · TP ${fmt(trade.takeProfit)}\n入场 Bid ${fmt(trade.entryBid)} / Ask ${fmt(trade.entryAsk)} · 点差 ${fmt(trade.entryAsk - trade.entryBid)}\n${attempt.reason}\n注意：这是本地模拟，不会发送真实订单。"
        paperTradeText.setTextColor(green)
        refreshPaperTradeStatus()
    }

    private fun updatePaperTradeFromQuote() {
        val trade = loadPaperTrade()
        if (trade == null || trade.status != PaperTradeStatus.OPEN) {
            refreshPaperTradeStatus()
            return
        }
        val quote = readFreshPaperQuote()
        if (quote == null) {
            paperTradeText.text = "无法检查模拟单：没有 MT5 屏幕 Bid/Ask。请刷新屏幕读取，过期报价不能触发模拟 SL/TP。"
            paperTradeText.setTextColor(red)
            return
        }
        val now = System.currentTimeMillis()
        val attempt = PaperTradingEngine.update(trade, quote, now)
        val updated = attempt.trade
        if (updated == null) {
            paperTradeText.text = attempt.reason
            paperTradeText.setTextColor(red)
            return
        }
        savePaperTrade(updated)
        if (updated.status == PaperTradeStatus.CLOSED) {
            recordPaperClosure(updated)
            paperTradeText.text = "${attempt.reason}\n方向：${updated.direction} · 入场：${fmt(updated.entryPrice)} · 出场：${fmt(updated.exitPrice)}\n价格变动：${fmt(updated.pnlPrice)} · 扣估算往返佣金后净盈亏：${fmtAccountMoney(updated.pnlUsd)}"
            paperTradeText.setTextColor(if ((updated.pnlUsd ?: 0.0) >= 0.0) green else red)
        } else {
            val closeSide = if (updated.direction == TradeDirection.BUY) quote.bid else quote.ask
            val floating = if (updated.direction == TradeDirection.BUY) closeSide - updated.entryPrice else updated.entryPrice - closeSide
            paperTradeText.text = "${attempt.reason}\n${updated.direction} · Entry ${fmt(updated.entryPrice)} · 当前平仓侧报价 ${fmt(closeSide)} · 浮动价格盈亏 ${fmt(floating)}\nSL ${fmt(updated.stopLoss)} · TP ${fmt(updated.takeProfit)}"
            paperTradeText.setTextColor(gold)
        }
        refreshPaperTradeStatus()
    }

    private fun closePaperTradeManually() {
        val trade = loadPaperTrade()
        if (trade == null || trade.status != PaperTradeStatus.OPEN) {
            paperTradeText.text = "当前没有未平仓的模拟单。"
            paperTradeText.setTextColor(muted)
            return
        }
        val quote = readFreshPaperQuote()
        if (quote == null) {
            paperTradeText.text = "手动平仓失败：需要 3 秒内的有效 MT5 Bid/Ask。"
            paperTradeText.setTextColor(red)
            return
        }
        val now = System.currentTimeMillis()
        val attempt = PaperTradingEngine.closeManually(trade, quote, now)
        val closed = attempt.trade
        if (closed == null) {
            paperTradeText.text = attempt.reason
            paperTradeText.setTextColor(red)
            return
        }
        savePaperTrade(closed)
        recordPaperClosure(closed)
        paperTradeText.text = "${attempt.reason}\n出场价：${fmt(closed.exitPrice)} · 价格变动：${fmt(closed.pnlPrice)} · 净盈亏：${fmtAccountMoney(closed.pnlUsd)}"
        paperTradeText.setTextColor(if ((closed.pnlUsd ?: 0.0) >= 0.0) green else red)
        refreshPaperTradeStatus()
    }

    private fun recordPaperClosure(trade: PaperTrade) {
        PaperTradeHistoryStore.append(this, trade)
        val pnl = trade.pnlUsd ?: return
        val wins = prefs.getInt("paper_money_wins", 0) + if (pnl > 0.0) 1 else 0
        val losses = prefs.getInt("paper_money_losses", 0) + if (pnl < 0.0) 1 else 0
        val flats = prefs.getInt("paper_money_flats", 0) + if (pnl == 0.0) 1 else 0
        val total = prefs.getFloat("paper_total_pnl_usd", 0f).toDouble() + pnl
        prefs.edit()
            .putInt("paper_money_wins", wins)
            .putInt("paper_money_losses", losses)
            .putInt("paper_money_flats", flats)
            .putFloat("paper_total_pnl_usd", total.toFloat())
            .apply()
    }

    private fun savePaperTrade(trade: PaperTrade) {
        val json = JSONObject()
            .put("direction", trade.direction.name)
            .put("entryPrice", trade.entryPrice)
            .put("plannedEntry", trade.plannedEntry)
            .put("stopLoss", trade.stopLoss)
            .put("takeProfit", trade.takeProfit)
            .put("entryBid", trade.entryBid)
            .put("entryAsk", trade.entryAsk)
            .put("entryTimestampMillis", trade.entryTimestampMillis)
            .put("source", trade.source)
            .put("lotSize", trade.lotSize)
            .put("contractSizeOunces", trade.contractSizeOunces)
            .put("commissionPerLotRoundTurnUsd", trade.commissionPerLotRoundTurnUsd)
            .put("status", trade.status.name)
        fun putNullable(key: String, value: Any?) {
            json.put(key, value ?: JSONObject.NULL)
        }
        putNullable("exitPrice", trade.exitPrice)
        putNullable("exitBid", trade.exitBid)
        putNullable("exitAsk", trade.exitAsk)
        putNullable("exitTimestampMillis", trade.exitTimestampMillis)
        putNullable("exitReason", trade.exitReason?.name)
        putNullable("pnlPrice", trade.pnlPrice)
        putNullable("pnlUsd", trade.pnlUsd)
        prefs.edit().putString("paper_trade_json", json.toString()).apply()
    }

    private fun loadPaperTrade(): PaperTrade? {
        val raw = prefs.getString("paper_trade_json", null) ?: return null
        return try {
            val json = JSONObject(raw)
            fun nullableDouble(key: String): Double? =
                if (json.isNull(key)) null else json.optDouble(key).takeIf { it.isFinite() }
            fun nullableLong(key: String): Long? =
                if (json.isNull(key)) null else json.optLong(key)
            PaperTrade(
                direction = TradeDirection.valueOf(json.getString("direction")),
                entryPrice = json.getDouble("entryPrice"),
                plannedEntry = json.getDouble("plannedEntry"),
                stopLoss = json.getDouble("stopLoss"),
                takeProfit = json.getDouble("takeProfit"),
                entryBid = json.getDouble("entryBid"),
                entryAsk = json.getDouble("entryAsk"),
                entryTimestampMillis = json.getLong("entryTimestampMillis"),
                source = json.optString("source", "UNKNOWN"),
                lotSize = json.optDouble("lotSize", 0.01),
                contractSizeOunces = json.optDouble("contractSizeOunces", 100.0),
                commissionPerLotRoundTurnUsd = json.optDouble("commissionPerLotRoundTurnUsd", 0.0),
                status = PaperTradeStatus.valueOf(json.getString("status")),
                exitPrice = nullableDouble("exitPrice"),
                exitBid = nullableDouble("exitBid"),
                exitAsk = nullableDouble("exitAsk"),
                exitTimestampMillis = nullableLong("exitTimestampMillis"),
                exitReason = if (json.isNull("exitReason")) null else PaperExitReason.valueOf(json.getString("exitReason")),
                pnlPrice = nullableDouble("pnlPrice"),
                pnlUsd = nullableDouble("pnlUsd")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun accountScale(): Double = if (paperAccountCurrency == "USC") 100.0 else 1.0

    private fun accountUnitLabel(): String = if (paperAccountCurrency == "USC") "USC" else "USD"

    private fun fmtAccountMoney(valueInUsd: Double?): String {
        if (valueInUsd == null || !valueInUsd.isFinite()) return "—"
        return String.format(Locale.US, "%.2f %s", valueInUsd * accountScale(), accountUnitLabel())
    }

    private fun refreshPaperTradeStatus() {
        if (!::paperTradeText.isInitialized) return
        val trade = loadPaperTrade()
        val startBalance = if (::paperBalanceInput.isInitialized) {
            paperBalanceInput.text.toString().trim().toDoubleOrNull()
                ?: (prefs.getString("paper_start_balance", "1000.00").orEmpty().toDoubleOrNull() ?: 1000.0)
        } else {
            prefs.getString("paper_start_balance", "1000.00").orEmpty().toDoubleOrNull() ?: 1000.0
        }
        val records = PaperTradeHistoryStore.load(this)
        val summary = PaperTradePerformance.summarize(records, startBalance / accountScale())
        val totalUsd = summary.totalNetUsd
        val realizedBalance = startBalance + totalUsd * accountScale()
        val pf = summary.profitFactor?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
        val stats = "已平仓 ${summary.trades} 笔 · 胜率 ${String.format(Locale.US, "%.1f", summary.winRatePercent)}% · Profit Factor $pf\n净盈亏 ${fmtAccountMoney(totalUsd)} · 模拟余额 ${String.format(Locale.US, "%.2f", realizedBalance)} ${accountUnitLabel()} · 最大回撤 ${fmtAccountMoney(summary.maxDrawdownUsd)}"
        if (::paperTradeHistoryText.isInitialized) {
            val recent = records.sortedByDescending { it.exitTimestampMillis }.take(5)
            paperTradeHistoryText.text = if (recent.isEmpty()) {
                "尚无已平仓模拟交易。"
            } else {
                recent.joinToString("\n") { item ->
                    val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.exitTimestampMillis))
                    "$time · ${item.direction} · ${fmt(item.entryPrice)} → ${fmt(item.exitPrice)} · ${fmtAccountMoney(item.pnlUsd)} · ${item.exitReason} · ${item.source}"
                }
            }
            paperTradeHistoryText.setTextColor(white)
        }
        val monitorStatus = prefs.getString("paper_monitor_status", "尚未开始自动检查").orEmpty()
        val observedPrefs = getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
        val quoteAge = System.currentTimeMillis() - observedPrefs.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        val quoteSource = observedPrefs.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val monitorFresh = quoteSource in setOf("ACCESSIBILITY", "SCREEN_OCR") &&
            quoteAge in 0L..PaperTradingEngine.MAX_QUOTE_AGE_MILLIS
        val monitorLine = if (monitorFresh) {
            "自动 SL/TP 监控：屏幕报价观察正常。$monitorStatus"
        } else {
            "自动 SL/TP 监控已暂停：没有 3 秒内的 MT5 屏幕报价。请切换到 MT5 并启用只读读取或屏幕 OCR。"
        }
        if (trade?.status == PaperTradeStatus.OPEN) {
            val closeSide = latestPaperCloseSide()
            val floatingUsd = if (closeSide == null) null else {
                val move = if (trade.direction == TradeDirection.BUY) closeSide - trade.entryPrice else trade.entryPrice - closeSide
                move * trade.contractSizeOunces * trade.lotSize -
                    trade.commissionPerLotRoundTurnUsd * trade.lotSize
            }
            val equity = if (floatingUsd == null) realizedBalance else realizedBalance + floatingUsd * accountScale()
            paperTradeText.text = "模拟持仓：${trade.direction} · Entry ${fmt(trade.entryPrice)} · SL ${fmt(trade.stopLoss)} · TP ${fmt(trade.takeProfit)}\n手数 ${trade.lotSize} · 合约 ${trade.contractSizeOunces} 盎司/手 · 开仓点差 ${fmt(trade.entryAsk - trade.entryBid)}\n预估浮动净盈亏：${fmtAccountMoney(floatingUsd)} · 模拟净值：${String.format(Locale.US, "%.2f", equity)} ${accountUnitLabel()}\n$monitorLine\n$stats"
            paperTradeText.setTextColor(if (monitorFresh) gold else red)
        } else if (trade?.status == PaperTradeStatus.CLOSED) {
            paperTradeText.text = "最近模拟单：${trade.direction} · 出场 ${fmt(trade.exitPrice)} · ${trade.exitReason}\n价格变动：${fmt(trade.pnlPrice)} · 扣估算往返佣金后净盈亏：${fmtAccountMoney(trade.pnlUsd)}\n$stats"
            paperTradeText.setTextColor(if ((trade.pnlUsd ?: 0.0) >= 0.0) green else red)
        } else {
            paperTradeText.text = "模拟账户尚无交易。确认 BUY/SELL 信号后才会开模拟单。\n$stats"
            paperTradeText.setTextColor(white)
        }
    }

    private fun latestPaperCloseSide(): Double? {
        val observation = getSharedPreferences(Mt5ScreenAccessibilityService.PREFS_NAME, MODE_PRIVATE)
        val source = observation.getString(Mt5ScreenAccessibilityService.KEY_SOURCE, "").orEmpty()
        val observedAt = observation.getLong(Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L)
        if (source !in setOf("ACCESSIBILITY", "SCREEN_OCR") ||
            System.currentTimeMillis() - observedAt !in 0L..PaperTradingEngine.MAX_QUOTE_AGE_MILLIS
        ) return null
        val symbol = observation.getString(Mt5ScreenAccessibilityService.KEY_SYMBOL, "").orEmpty()
        if (symbol.uppercase().replace("/", "").trim() !in setOf("XAUUSD", "GOLD")) return null
        val key = if (loadPaperTrade()?.direction == TradeDirection.BUY) Mt5ScreenAccessibilityService.KEY_BID else Mt5ScreenAccessibilityService.KEY_ASK
        return observation.getString(key, "").orEmpty().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun refreshRiskStatus() {
        val state = RiskStateStore.get(this)
        riskStatusText.text = String.format(
            Locale.US,
            "今日已记录亏损：%.2f%%\n连续亏损：%d 笔\n闸门限制：单日亏损达到 3%% 或连续亏损达到 3 笔后，REAL 信号将被拦截。",
            state.dailyLossPercent,
            state.consecutiveLosses
        )
        riskStatusText.setTextColor(
            if (state.dailyLossPercent >= 3.0 || state.consecutiveLosses >= 3) red else white
        )
    }

    private fun getRealMarketKey(): String {
        val typed = apiKeyInput.text.toString().trim()
        if (typed.isNotBlank()) return typed
        return EncryptedApiKeyStore.get(this, "realmarket_api_key")
    }

    private fun fetchPrice() {
        val key = getRealMarketKey()
        if (key.isBlank()) {
            statusText.text = "状态：请先输入 RealMarketAPI Free Key"
            return
        }
        if (requestInProgress) {
            statusText.text = "状态：正在请求行情"
            return
        }
        requestInProgress = true
        statusText.text = "状态：正在连接 RealMarketAPI…"
        client.fetchPrice(apiKey = key, symbol = "XAUUSD", timeframe = "M1") { result ->
            requestInProgress = false
            result.onSuccess { quote -> showPrice(quote) }
                .onFailure { error ->
                    statusText.text = "状态：请求失败"
                    statusText.setTextColor(red)
                    priceText.text = "暂无有效报价"
                    updateText.text = error.message ?: "未知错误"
                }
        }
    }

    private fun showPrice(q: RealMarketPrice) {
        latestQuote = q
        lastQuotePrice = q.close
        lastQuoteReceivedAt = System.currentTimeMillis()
        lastQuoteSourceTimestamp = SourceQuoteTimestamp.parseMillis(q.openTime)
        statusText.text = "状态：已取得 REST 参考数据（非实时）"
        statusText.setTextColor(green)
        priceText.text = String.format(Locale.US, "%.2f", q.close)
        val spread = q.spread?.let { String.format(Locale.US, "%.3f", it) } ?: "—"
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
            q.open, q.high, q.low, q.close, q.volume?.toString() ?: "—"
        )
        updateText.text = "来源 K 线时间：${q.openTime}\n本机获取时间：" +
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun loadHistoricalAnalysis() {
        val typedKey = twelveDataKeyInput.text.toString().trim()
        if (typedKey.isNotBlank()) ApiKeyStore.save(this, typedKey)
        val key = ApiKeyStore.get(this)
        if (key.isBlank()) {
            analysisText.text = "请先输入并保存 Twelve Data API Key。"
            return
        }
        analysisText.text = "正在获取历史 K 线：M5 最多 2500 根，M15 1000 根，H1 500 根…"
        decisionText.text = "WAIT"
        decisionText.setTextColor(gold)
        decisionReasonText.text = "数据下载期间不会生成新方案。"

        ioExecutor.execute {
            try {
                val provider = HistoricalCandleProvider(apiKey = key, symbol = "XAU/USD")
                val candleMap = linkedMapOf<Timeframe, List<Candle>>()
                val timeframes = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)
                for (timeframe in timeframes) {
                    // M5 needs enough depth to cover more than a few trading days.
                    // M15/H1 retain 500 bars to limit request size and startup time.
                    val requestedBars = when (timeframe) { Timeframe.M1 -> 500; Timeframe.M5 -> 2500; Timeframe.M15 -> 1000; Timeframe.H1 -> 500 }
                    val result = provider.getHistoricalCandles(timeframe, requestedBars)
                    if (result.isFailure) throw result.exceptionOrNull()
                        ?: IllegalStateException("历史数据请求失败")
                    candleMap[timeframe] = result.getOrThrow()
                }

                val analysis = MultiTimeframeAnalyzer.analyze(candleMap)
                liveFeedController.seedHistoricalCandles(candleMap)
                mt5QuoteController.seedHistoricalCandles(candleMap)
                latestCandles = candleMap.toMap()
                latestAnalysis = analysis
                val rendered = buildString {
                    appendLine("数据来源：Twelve Data · XAU/USD")
                    appendLine("历史深度：M5 ${candleMap[Timeframe.M5].orEmpty().size} 根；M15 ${candleMap[Timeframe.M15].orEmpty().size} 根；H1 ${candleMap[Timeframe.H1].orEmpty().size} 根。实际返回数量可能受数据源套餐限制。")
                    for (tf in timeframes) {
                        val candles = candleMap[tf].orEmpty()
                        val item = when (tf) {
                            Timeframe.M5 -> analysis.m5
                            Timeframe.M15 -> analysis.m15
                            else -> analysis.h1
                        }
                        appendLine()
                        appendLine("${tf.name}：${candles.size} 根 K 线；趋势=${trendLabel(item.structure.trend)}；可用于决策=${item.available}")
                        appendLine("EMA9=${fmt(item.indicators.ema9)}  EMA20=${fmt(item.indicators.ema20)}  EMA50=${fmt(item.indicators.ema50)}  EMA200=${fmt(item.indicators.ema200)}")
                        appendLine("RSI14=${fmt(item.indicators.rsi14)}  MACD柱=${fmt(item.indicators.macdHistogram)}  ADX14=${fmt(item.indicators.adx14)}  ATR14=${fmt(item.indicators.atr14)}")
                        appendLine("旧式区间极值：支撑=${fmt(item.structure.support)}  阻力=${fmt(item.structure.resistance)}  突破=${item.structure.breakout}")
                        val swing = SwingSupportResistanceAnalyzer.analyze(
                            candles = candles,
                            leftBars = 3,
                            rightBars = 3,
                            currentPrice = candles.lastOrNull()?.close,
                            atr = item.indicators.atr14
                        )
                        appendLine("确认 Swing High（最近）：${swing.swingHighs.take(3).joinToString { fmt(it.price) }.ifBlank { "—" }}")
                        appendLine("确认 Swing Low（最近）：${swing.swingLows.take(3).joinToString { fmt(it.price) }.ifBlank { "—" }}")
                        appendLine("支撑区（按最近历史收盘价排序）：${swing.supportZones.take(3).joinToString(" | ") { "${fmt(it.low)}–${fmt(it.high)}（触碰 ${it.touches} 次）" }.ifBlank { "—" }}")
                        appendLine("阻力区（按最近历史收盘价排序）：${swing.resistanceZones.take(3).joinToString(" | ") { "${fmt(it.low)}–${fmt(it.high)}（触碰 ${it.touches} 次）" }.ifBlank { "—" }}")
                    }
                    append("提示：这些指标依赖数据源与周期，需先验证数据准确性。")
                }

                runOnUiThread {
                    analysisText.text = rendered
                    latestCandles = candleMap.toMap()
                    latestAnalysis = analysis
                    decisionText.text = "WAIT"
                    decisionText.setTextColor(gold)
                    decisionReasonText.text = "历史数据已加载。正在获取 REST 参考数据；Free 版不生成可跟随信号。"
                    refreshQuoteAndEvaluate(candleMap, analysis)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    analysisText.text = "历史分析失败：${error.message ?: "未知错误"}"
                    decisionText.text = "NO TRADE"
                    decisionText.setTextColor(red)
                    decisionReasonText.text = "数据获取或解析失败。检查 Twelve Data Key、套餐权限、网络和请求额度。"
                }
            }
        }
    }

    /**
     * Re-analyzes locally aggregated M5/M15/H1 candles after a verified
     * third-party spot tick. This is reference analysis only: until a matching
     * MT5 broker quote is independently verified, it must never produce an
     * actionable BUY/SELL setup.
     */
    private fun refreshLiveFeedAnalysis(tick: MarketTick) {
        val now = System.currentTimeMillis()
        if (now - lastLiveAnalysisQueuedAt < 1500L) return
        lastLiveAnalysisQueuedAt = now

        val candleMap = linkedMapOf(
            Timeframe.M5 to liveFeedController.getCandles(Timeframe.M5),
            Timeframe.M15 to liveFeedController.getCandles(Timeframe.M15),
            Timeframe.H1 to liveFeedController.getCandles(Timeframe.H1)
        )

        if (candleMap.values.any { it.size < 50 }) {
            runOnUiThread {
                if (::liveFeedAnalysisText.isInitialized) {
                    liveFeedAnalysisText.text =
                        "实时报价已接收，但历史 K 线不足（M5/M15/H1 每周期至少 50 根）。先点击“加载历史数据并分析”。当前仍为 NO TRADE。"
                    liveFeedAnalysisText.setTextColor(gold)
                }
                if (::decisionText.isInitialized) {
                    decisionText.text = "NO TRADE"
                    decisionText.setTextColor(red)
                    decisionReasonText.text =
                        "第三方现货报价不是已核验的 MT5 经纪商报价；且历史 K 线不足。不能把该数据当成可执行进场信号。"
                }
            }
            return
        }

        val stableCandles = candleMap.mapValues { (_, candles) -> candles.toList() }
        ioExecutor.execute {
            try {
                val analysis = MultiTimeframeAnalyzer.analyze(stableCandles)
                val m15 = analysis.m15
                val swing = SwingSupportResistanceAnalyzer.analyze(
                    candles = stableCandles[Timeframe.M15].orEmpty(),
                    leftBars = 3,
                    rightBars = 3,
                    currentPrice = tick.midPrice,
                    atr = m15.indicators.atr14
                )
                val rendered = buildString {
                    appendLine("实时结构参考 · 来源：GoldPrice.dev 现货流")
                    appendLine("最新中间价：${fmt(tick.midPrice)} · 点差：${fmt(tick.spread)}")
                    appendLine("M5：${trendLabel(analysis.m5.structure.trend)} · M15：${trendLabel(m15.structure.trend)} · H1：${trendLabel(analysis.h1.structure.trend)}")
                    appendLine("M15 EMA20：${fmt(m15.indicators.ema20)} · RSI14：${fmt(m15.indicators.rsi14)} · ATR14：${fmt(m15.indicators.atr14)}")
                    appendLine("支撑区：${swing.supportZones.take(3).joinToString(" | ") { "${fmt(it.low)}–${fmt(it.high)}（${it.touches} 次触碰）" }.ifBlank { "尚未识别" }}")
                    appendLine("阻力区：${swing.resistanceZones.take(3).joinToString(" | ") { "${fmt(it.low)}–${fmt(it.high)}（${it.touches} 次触碰）" }.ifBlank { "尚未识别" }}")
                    appendLine()
                    append("限制：第三方现货报价可能与 MT5 经纪商报价和点差不同；这部分只显示结构偏向，不授权进场。")
                }
                runOnUiThread {
                    if (::liveFeedAnalysisText.isInitialized) {
                        liveFeedAnalysisText.text = rendered
                        liveFeedAnalysisText.setTextColor(white)
                    }
                    if (::decisionText.isInitialized) {
                        decisionText.text = "NO TRADE"
                        decisionText.setTextColor(red)
                        decisionReasonText.text =
                            "已更新实时技术结构参考，但未核对 MT5 经纪商 Bid/Ask、实际点差和订单规格。当前行情来源不具备执行资格。最终进场信号仍被锁定。"
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (::liveFeedAnalysisText.isInitialized) {
                        liveFeedAnalysisText.text =
                            "实时结构分析失败：${error.message ?: "未知错误"}。保留 NO TRADE。"
                        liveFeedAnalysisText.setTextColor(gold)
                    }
                }
            }
        }
    }

    /**
     * Uses a fresh, user-confirmed quote parsed from the MT5 screen.
     * The app never treats OCR alone as authorization: the user must tap the
     * explicit confirmation button, and all quote/history/risk gates still run.
     */
    private fun evaluateMt5ObservedQuote() {
        val observation = getSharedPreferences(
            Mt5ScreenAccessibilityService.PREFS_NAME,
            MODE_PRIVATE
        )
        val observedAt = observation.getLong(
            Mt5ScreenAccessibilityService.KEY_OBSERVED_AT,
            0L
        )
        val source = observation.getString(
            Mt5ScreenAccessibilityService.KEY_SOURCE,
            ""
        ).orEmpty()
        val symbol = observation.getString(
            Mt5ScreenAccessibilityService.KEY_SYMBOL,
            ""
        ).orEmpty().uppercase().replace("/", "")
        val bid = observation.getString(
            Mt5ScreenAccessibilityService.KEY_BID,
            ""
        ).orEmpty().toDoubleOrNull()
        val ask = observation.getString(
            Mt5ScreenAccessibilityService.KEY_ASK,
            ""
        ).orEmpty().toDoubleOrNull()
        val ageMillis = System.currentTimeMillis() - observedAt

        fun reject(reason: String) {
            showDecision(
                DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = "MT5 报价验证失败：$reason"
                )
            )
        }

        if (source != "ACCESSIBILITY" && source != "SCREEN_OCR") {
            reject("没有来自 MT5 的有效屏幕观察记录。先启用只读读取或屏幕 OCR。")
            return
        }
        if (observedAt <= 0L || ageMillis !in 0L..3000L) {
            reject("屏幕报价已过期（必须在 3 秒内重新读取）。")
            return
        }
        if (symbol !in setOf("XAUUSD", "GOLD")) {
            reject("未能确认屏幕品种为 XAUUSD。识别到：${symbol.ifBlank { "未知" }}")
            return
        }
        if (bid == null || ask == null || !bid.isFinite() || !ask.isFinite() ||
            bid <= 0.0 || ask <= bid
        ) {
            reject("Bid/Ask 不完整或不合理。请确认 MT5 报价区域可见，且识别结果正确。")
            return
        }

        val history = latestCandles
        val priorAnalysis = latestAnalysis
        if (priorAnalysis == null ||
            listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1).any {
                history[it].orEmpty().size < 50
            }
        ) {
            reject("缺少足够的 M5/M15/H1 历史 K 线。先点击“加载历史数据并分析”。")
            return
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        val staleTimeframe = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1).firstOrNull { tf ->
            val last = history[tf].orEmpty().lastOrNull() ?: return@firstOrNull true
            val ageSeconds = nowSeconds - last.timestamp
            ageSeconds < -tf.seconds || ageSeconds > tf.seconds * 2L + 60L
        }
        if (staleTimeframe != null) {
            reject("${staleTimeframe.name} 历史 K 线过旧。请重新加载历史数据。")
            return
        }

        val atr = priorAnalysis.m15.indicators.atr14
        if (atr == null || !atr.isFinite() || atr <= 0.0) {
            reject("M15 ATR 不可用，无法进行风险与价格差异检查。")
            return
        }

        val midpoint = bid + (ask - bid) / 2.0
        val latestM5Close = history[Timeframe.M5].orEmpty().last().close
        if (kotlin.math.abs(midpoint - latestM5Close) > atr) {
            reject(
                "MT5 当前价与最近 M5 历史收盘价差距超过 1 个 M15 ATR。" +
                    "可能是数据源差异、行情跳变或历史数据过旧；重新加载后再试。"
            )
            return
        }

        val mt5Tick = MarketTick(
            symbol = "XAUUSD",
            bid = bid,
            ask = ask,
            timestamp = observedAt,
            source = "MT5 screen quote (user-confirmed)"
        )
        if (!mt5QuoteController.submitTick(mt5Tick)) {
            reject("报价未通过时间新鲜度或重复行情检查。请等待 MT5 更新后再确认。")
            return
        }

        val currentCandles = linkedMapOf(
            Timeframe.M5 to mt5QuoteController.getCandles(Timeframe.M5),
            Timeframe.M15 to mt5QuoteController.getCandles(Timeframe.M15),
            Timeframe.H1 to mt5QuoteController.getCandles(Timeframe.H1)
        )
        val analysis = try {
            MultiTimeframeAnalyzer.analyze(currentCandles)
        } catch (error: Exception) {
            reject("无法分析当前 K 线：${error.message ?: "未知错误"}")
            return
        }

        val snapshot = MarketSnapshot(
            symbol = "XAUUSD",
            bid = bid,
            ask = ask,
            timestamp = observedAt,
            candles = currentCandles,
            source = "MT5 screen quote (user-confirmed)",
            receivedAt = observedAt
        )
        val gated = TradeDecisionGate.evaluate(
            snapshot = snapshot,
            analysis = analysis,
            candles = currentCandles,
            riskState = RiskStateStore.get(this)
        )
        latestCandles = currentCandles
        latestAnalysis = analysis
        showDecision(gated.decision)
        decisionReasonText.text = decisionReasonText.text.toString() +
            "\n\n报价来源：MT5 屏幕读取，经你手动确认；报价年龄：${ageMillis} ms。" +
            "\n历史 K 线来源仍为 Twelve Data。信号未经充分回测，不保证盈利；应用不会自动下单。"
    }

    private fun runStrategyBacktest() {
        val roundTripCostPrice = backtestCostInput.text.toString().trim().toDoubleOrNull()
        if (roundTripCostPrice == null || !roundTripCostPrice.isFinite() || roundTripCostPrice < 0.0) {
            backtestText.text = "无法回测：往返成本必须是大于或等于 0 的有效数字。"
            backtestText.setTextColor(red)
            return
        }
        val candles = latestCandles.mapValues { (_, series) -> series.toList() }
        if (listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1).any {
                candles[it].orEmpty().size < 50
            }
        ) {
            backtestText.text = "无法回测：请先成功加载 M5、M15、H1 历史 K 线。"
            backtestText.setTextColor(red)
            return
        }

        backtestText.text = "正在逐根 K 线回放策略；只使用信号发生时已收盘的数据……"
        backtestText.setTextColor(gold)
        ioExecutor.execute {
            try {
                val result = StrategyBacktester.run(
                    candles,
                    maxHoldBars = 48,
                    roundTripCostPrice = roundTripCostPrice
                )
                val profitFactor = result.profitFactor?.let { fmt(it) }
                    ?: if (result.totalR > 0.0) "没有亏损交易样本" else "—"
                val rendered = buildString {
                    appendLine("历史回测（研究用途，不是未来收益预测）")
                    appendLine("M5 样本：${result.sampleBars} 根 · 覆盖约 ${String.format(Locale.US, "%.1f", result.sampleDurationDays)} 天")
                    appendLine("交易：${result.trades.size} · 盈利：${result.wins} · 亏损：${result.losses} · 胜率：${String.format(Locale.US, "%.1f", result.winRatePercent)}%")
                    appendLine("往返成本假设：${fmt(result.roundTripCostPrice)} 美元价格距离/笔（点差、滑点及手续费的折算值；0 表示未计成本）")
                    appendLine("毛累计结果（未扣成本）：${fmt(result.grossTotalR)} R")
                    appendLine("净累计结果（已扣假设成本）：${fmt(result.totalR)} R · 单笔净期望：${fmt(result.expectancyR)} R · 净 Profit Factor：$profitFactor")
                    appendLine("最大回撤：${fmt(result.maxDrawdownR)} R · 最长持仓：48 根 M5 K 线")
                    appendLine("多空分布：BUY ${result.trades.count { it.direction == com.goldbee.decision.TradeDirection.BUY }} 笔 · SELL ${result.trades.count { it.direction == com.goldbee.decision.TradeDirection.SELL }} 笔")
                    appendLine("最近交易明细（最多 10 笔，按时间顺序）：")
                    if (result.trades.isEmpty()) {
                        appendLine("没有符合当前策略条件的交易。")
                    } else {
                        result.trades.takeLast(10).forEach { trade ->
                            val entryTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(Date(trade.entryTimeSeconds * 1000L))
                            val rText = String.format(Locale.US, "净 %+.2fR / 毛 %+.2fR", trade.rMultiple, trade.grossRMultiple)
                            appendLine(
                                "$entryTime ${trade.direction} · 入场 ${fmt(trade.entryPrice)} · SL ${fmt(trade.stopLoss)} · TP ${fmt(trade.takeProfit)} · 出场 ${fmt(trade.exitPrice)} · $rText · ${trade.exitType}"
                            )
                        }
                    }
                    appendLine("回放规则：信号使用当时已收盘的 M5/M15/H1 K 线；下一根 M5 开盘进场；同一根 K 线同时触及止损和止盈时，按止损先发生。")
                    appendLine("限制：只扣除你输入的固定往返成本折算值；未使用历史 Bid/Ask 逐笔数据，也未模拟动态点差、逐笔滑点、执行延迟、拒单及经纪商报价差异。成本设为 0 时属于未扣成本的乐观结果。")
                    if (result.sampleIsTooSmall) {
                        append("警告：样本少于 30 笔或覆盖不足 7 天，不能据此判断策略有盈利优势。")
                    } else {
                        append("仍需扩大样本并进行样本外验证；历史结果不保证未来盈利。")
                    }
                }
                runOnUiThread {
                    backtestText.text = rendered
                    backtestText.setTextColor(if (result.sampleIsTooSmall) gold else white)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    backtestText.text = "回测失败：${error.message ?: "未知错误"}"
                    backtestText.setTextColor(red)
                }
            }
        }
    }

    private fun fmt(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.3f", it) } ?: "—"

    private fun trendLabel(trend: Trend): String = when (trend) {
        Trend.BULLISH -> "偏多"
        Trend.BEARISH -> "偏空"
        Trend.SIDEWAYS -> "震荡/不明"
    }

    override fun onDestroy() {
        polling = false
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(liveFeedHealthRunnable)
        liveFeedClient?.disconnect()
        liveFeedClient = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val SCREEN_CAPTURE_REQUEST_CODE = 7401
        private const val OBSERVER_REFRESH_INTERVAL_MS = 1000L
        private const val LIVE_FEED_HEALTH_INTERVAL_MS = 1000L
        private const val LIVE_TICK_MAX_AGE_MS = 3000L
    }
}