package com.goldbee

import android.graphics.Color
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
import com.goldbee.analysis.MultiTimeframeAnalyzer
import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.Trend
import com.goldbee.decision.CopySignalEvaluator
import com.goldbee.decision.CopySignalParser
import com.goldbee.decision.DecisionAction
import com.goldbee.decision.DecisionResult
import com.goldbee.decision.TradeDecisionGate
import com.goldbee.market.Candle
import com.goldbee.market.HistoricalCandleProvider
import com.goldbee.market.MarketSnapshot
import com.goldbee.market.RealMarketPrice
import com.goldbee.market.RealMarketRestClient
import com.goldbee.market.RealMarketPlanPolicy
import com.goldbee.market.RealMarketApiPlan
import com.goldbee.market.SourceQuoteTimestamp
import com.goldbee.market.Timeframe
import com.goldbee.settings.ApiKeyStore
import com.goldbee.settings.EncryptedApiKeyStore
import com.goldbee.risk.RiskStateStore
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
    private lateinit var twelveDataKeyInput: EditText
    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var bidAskText: TextView
    private lateinit var candleText: TextView
    private lateinit var updateText: TextView
    private lateinit var analysisText: TextView
    private lateinit var decisionText: TextView
    private lateinit var decisionReasonText: TextView
    private lateinit var copySignalInput: EditText
    private lateinit var copyResultText: TextView
    private lateinit var riskLossInput: EditText
    private lateinit var riskStatusText: TextView
    @Volatile private var latestCandles: Map<Timeframe, List<Candle>> = emptyMap()
    @Volatile private var latestAnalysis: MultiTimeframeAnalysis? = null
    @Volatile private var lastQuotePrice: Double? = null
    @Volatile private var latestQuote: RealMarketPrice? = null
    @Volatile private var lastQuoteReceivedAt: Long = 0L
    @Volatile private var lastQuoteSourceTimestamp: Long? = null
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

    private fun addButton(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit) {
        val button = Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 12f
            setTextColor(if (primary) bg else white)
            setBackgroundColor(if (primary) gold else Color.rgb(39, 46, 59))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
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
            apiKeyInput.hint = "实时行情 Key 已安全保存；留空继续使用"
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
                ApiKeyStore.get(this).isBlank() -> "状态：实时行情 Key 已保存；还需 Twelve Data Key 才能分析历史数据"
                else -> "状态：API Key 已安全保存"
            }
        }
        addButton(saveRow, "刷新参考数据") { fetchPrice() }
        settings.addView(saveRow)
        root.addView(settings)

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

        val analysis = makeCard()
        addLabel(analysis, "多周期技术分析", 17f, white, true)
        analysisText = addLabel(
            analysis,
            "尚未加载历史 K 线。加载 M5、M15、H1 后计算 EMA、RSI、MACD、ADX、ATR 与支撑阻力。",
            12f,
            white
        )
        addButton(analysis, "加载历史数据并分析", true) { loadHistoricalAnalysis() }
        root.addView(analysis)

        val decision = makeCard()
        addLabel(decision, "研究信号（未回测）", 17f, white, true)
        decisionText = addLabel(decision, "NO TRADE", 25f, red, true)
        decisionReasonText = addLabel(decision, "Free 免费版没有连续实时行情流。当前仅开放历史技术分析；REAL/COPY 不生成可跟随信号。", 12f, white)
        addLabel(
            decision,
            "注意：信号逻辑尚未证明具有盈利优势。仅用于研究，不代表盈利保证；不自动下单，真实交易由你在 MT5 中决定。",
            11f,
            gold
        )
        root.addView(decision)

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
            "粘贴信号后，应用会用最近获取的报价和已加载的 M15 分析重新检查。没有明确 Entry、SL 或 TP，或行情过期时，一律不允许跟随。",
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
        copyResultText = addLabel(copyCard, "等待审核。先刷新报价并加载 M5、M15、H1 历史数据。", 13f, white)
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

        if (!RealMarketPlanPolicy.allowsActionableSignals(realMarketApiPlan)) {
            copyResultText.setTextColor(red)
            copyResultText.text = RealMarketPlanPolicy.blockReason(realMarketApiPlan)
            return
        }

        val currentPrice = lastQuotePrice
        val quoteAge = System.currentTimeMillis() - lastQuoteReceivedAt
        if (currentPrice == null || !currentPrice.isFinite() || currentPrice <= 0.0 ||
            quoteAge !in 0L..30_000L ||
            !SourceQuoteTimestamp.isFresh(lastQuoteSourceTimestamp)
        ) {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE：报价不存在、来源时间无法验证或行情 K 线已超过允许时效。刷新报价后重试；来源时间无法验证时不能跟随。"
            return
        }

        val analysis = latestAnalysis
        val candles = latestCandles
        if (analysis == null || candles[Timeframe.M15].orEmpty().size < 50) {
            copyResultText.setTextColor(red)
            copyResultText.text = "NO TRADE：尚无足够的 M15 历史数据。先点击“加载历史数据并分析”。"
            return
        }

        val evaluation = CopySignalEvaluator.evaluate(
            signal = signal,
            currentPrice = currentPrice,
            analysis = analysis,
            candles = candles
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

    private fun showDecision(decision: DecisionResult) {
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
            String.format(
                Locale.US,
                "%s\nEntry：%.3f\nSL：%.3f\nTP：%.3f\nR:R：1:%.1f\n模型评分置信度：%.0f%%\n原因：%s",
                decision.reason,
                setup.entry,
                setup.stopLoss,
                setup.takeProfit,
                setup.riskReward,
                decision.confidence * 100.0,
                setup.reason
            )
        }
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
        analysisText.text = "正在获取 M5、M15、H1 历史 K 线…"
        decisionText.text = "WAIT"
        decisionText.setTextColor(gold)
        decisionReasonText.text = "数据下载期间不会生成新方案。"

        ioExecutor.execute {
            try {
                val provider = HistoricalCandleProvider(apiKey = key, symbol = "XAU/USD")
                val candleMap = linkedMapOf<Timeframe, List<Candle>>()
                val timeframes = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)
                for (timeframe in timeframes) {
                    val result = provider.getHistoricalCandles(timeframe, 200)
                    if (result.isFailure) throw result.exceptionOrNull()
                        ?: IllegalStateException("历史数据请求失败")
                    candleMap[timeframe] = result.getOrThrow()
                }

                val analysis = MultiTimeframeAnalyzer.analyze(candleMap)
                latestCandles = candleMap.toMap()
                latestAnalysis = analysis
                val rendered = buildString {
                    appendLine("数据来源：Twelve Data · XAU/USD")
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
                        appendLine("支撑=${fmt(item.structure.support)}  阻力=${fmt(item.structure.resistance)}  突破=${item.structure.breakout}")
                    }
                    append("提示：这些指标依赖数据源与周期，需先验证数据准确性。")
                }

                runOnUiThread {
                    analysisText.text = rendered
                    latestCandles = candleMap.toMap()
                    latestAnalysis = analysis
                    decisionText.text = "WAIT"
                    decisionText.setTextColor(gold)
                    decisionReasonText.text = "历史数据已加载。正在刷新报价，并检查报价时效、点差、入场距离和盈亏比。"
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
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}