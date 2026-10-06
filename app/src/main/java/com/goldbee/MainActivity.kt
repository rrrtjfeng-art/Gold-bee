package com.goldbee

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(15, 15, 18)
    private val cardColor = Color.rgb(27, 27, 32)
    private val whiteColor = Color.WHITE
    private val grayColor = Color.rgb(180, 180, 185)
    private val yellowColor = Color.rgb(255, 193, 7)
    private val redColor = Color.rgb(244, 67, 54)
    private val greenColor = Color.rgb(76, 175, 80)

    private lateinit var priceText: TextView
    private lateinit var dataStatusText: TextView
    private lateinit var updateText: TextView
    private lateinit var decisionText: TextView
    private lateinit var reasonText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildInterface()
    }

    private fun buildInterface() {

        val scrollView = ScrollView(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 28, 24, 32)
        root.setBackgroundColor(backgroundColor)

        root.addView(
            createText(
                "GOLD BEE",
                30f,
                whiteColor,
                true,
                Gravity.CENTER
            )
        )

        root.addView(
            createText(
                "XAUUSD DECISION SYSTEM",
                14f,
                grayColor,
                false,
                Gravity.CENTER
            )
        )

        addSpace(root, 24)

        // =========================
        // MARKET
        // =========================

        root.addView(createSection("MARKET"))

        val marketCard = createCard()

        marketCard.addView(
            createLabelValue(
                "Symbol",
                "XAUUSD"
            )
        )

        priceText = createLabelValue(
            "Current Price",
            "---"
        )
        marketCard.addView(priceText)

        dataStatusText = createLabelValue(
            "Market Data",
            "NOT CONNECTED"
        )
        marketCard.addView(dataStatusText)

        updateText = createLabelValue(
            "Last Update",
            "---"
        )
        marketCard.addView(updateText)

        val refreshButton = createButton("REFRESH")

        refreshButton.setOnClickListener {
            refreshMarket()
        }

        marketCard.addView(refreshButton)

        root.addView(marketCard)

        addSpace(root, 20)

        // =========================
        // TIMEFRAME
        // =========================

        root.addView(createSection("TIMEFRAME"))

        val timeframeRow = LinearLayout(this)
        timeframeRow.orientation = LinearLayout.HORIZONTAL
        timeframeRow.gravity = Gravity.CENTER

        val h1Button = createButton("H1")
        val m15Button = createButton("M15")
        val m5Button = createButton("M5")

        timeframeRow.addView(h1Button)
        timeframeRow.addView(m15Button)
        timeframeRow.addView(m5Button)

        root.addView(timeframeRow)

        addSpace(root, 20)

        // =========================
        // DECISION
        // =========================

        root.addView(createSection("DECISION"))

        val decisionCard = createCard()

        decisionText = createText(
            "WAIT",
            34f,
            yellowColor,
            true,
            Gravity.CENTER
        )

        decisionCard.addView(decisionText)

        decisionCard.addView(
            createText(
                "NO TRADE",
                14f,
                grayColor,
                true,
                Gravity.CENTER
            )
        )

        root.addView(decisionCard)

        addSpace(root, 20)

        // =========================
        // TRADE SETUP
        // =========================

        root.addView(createSection("TRADE SETUP"))

        val setupCard = createCard()

        setupCard.addView(
            createLabelValue(
                "Direction",
                "---"
            )
        )

        setupCard.addView(
            createLabelValue(
                "Entry",
                "---"
            )
        )

        setupCard.addView(
            createLabelValue(
                "Stop Loss",
                "---"
            )
        )

        setupCard.addView(
            createLabelValue(
                "Take Profit",
                "---"
            )
        )

        setupCard.addView(
            createLabelValue(
                "Risk : Reward",
                "---"
            )
        )

        root.addView(setupCard)

        addSpace(root, 20)

        // =========================
        // ANALYSIS
        // =========================

        root.addView(createSection("ANALYSIS"))

        val analysisCard = createCard()

        analysisCard.addView(
            createLabelValue(
                "Trend",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            createLabelValue(
                "Market Structure",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            createLabelValue(
                "Momentum",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            createLabelValue(
                "Volatility",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            createLabelValue(
                "Multi-Timeframe",
                "WAITING FOR DATA"
            )
        )

        root.addView(analysisCard)

        addSpace(root, 20)

        // =========================
        // REASON
        // =========================

        root.addView(createSection("REASON"))

        val reasonCard = createCard()

        reasonText = createText(
            "系统尚未连接真实黄金行情。\n\n" +
                    "没有有效行情数据时，系统不会产生 BUY 或 SELL。\n\n" +
                    "当前状态：等待 Market Data Provider。",
            15f,
            grayColor,
            false,
            Gravity.START
        )

        reasonCard.addView(reasonText)

        root.addView(reasonCard)

        addSpace(root, 20)

        // =========================
        // MODE
        // =========================

        root.addView(createSection("MODE"))

        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        modeRow.gravity = Gravity.CENTER

        val realButton = createButton("REAL")
        val copyButton = createButton("COPY")

        realButton.setOnClickListener {
            decisionText.text = "WAIT"
            decisionText.setTextColor(yellowColor)

            reasonText.text =
                "REAL 模式\n\n" +
                        "系统等待真实 XAUUSD 行情数据。\n\n" +
                        "没有有效行情，不进行交易判断。"
        }

        copyButton.setOnClickListener {
            decisionText.text = "WAIT"
            decisionText.setTextColor(yellowColor)

            reasonText.text =
                "COPY 模式\n\n" +
                        "需要用户输入完整黄金信号。\n\n" +
                        "如果信号没有明确 Entry，系统将直接 NO TRADE。\n\n" +
                        "系统不会盲目追价。"
        }

        modeRow.addView(realButton)
        modeRow.addView(copyButton)

        root.addView(modeRow)

        addSpace(root, 20)

        // =========================
        // EXECUTION SAFETY
        // =========================

        root.addView(createSection("EXECUTION SAFETY"))

        val safetyCard = createCard()

        safetyCard.addView(
            createLabelValue(
                "Automatic Order",
                "DISABLED"
            )
        )

        safetyCard.addView(
            createLabelValue(
                "User Confirmation",
                "REQUIRED"
            )
        )

        safetyCard.addView(
            createLabelValue(
                "MT5",
                "NOT CONNECTED"
            )
        )

        safetyCard.addView(
            createText(
                "本系统不会自动下单。\n" +
                        "任何真实交易都必须经过用户明确确认。",
                14f,
                redColor,
                true,
                Gravity.CENTER
            )
        )

        root.addView(safetyCard)

        addSpace(root, 30)

        root.addView(
            createText(
                "GOLD BEE v1.1\nMarket Data Foundation",
                12f,
                grayColor,
                false,
                Gravity.CENTER
            )
        )

        scrollView.addView(root)

        setContentView(scrollView)
    }

    private fun refreshMarket() {

        /*
         * 这里故意不制造假价格。
         *
         * 下一阶段：
         *
         * MarketDataProvider
         *        ↓
         * MarketSnapshot
         *        ↓
         * Analysis Engine
         *        ↓
         * Decision Engine
         *
         * 当前先验证 UI 与数据入口。
         */

        priceText.text = "Current Price\n---"

        dataStatusText.text =
            "Market Data\nWAITING FOR PROVIDER"

        updateText.text =
            "Last Update\nWAITING"

        decisionText.text = "WAIT"
        decisionText.setTextColor(yellowColor)

        reasonText.text =
            "刷新已执行。\n\n" +
                    "当前没有真实 XAUUSD 数据源，因此系统保持 WAIT / NO TRADE。\n\n" +
                    "下一阶段接入真实 MarketDataProvider。"
    }

    private fun createSection(title: String): TextView {

        return createText(
            title,
            16f,
            yellowColor,
            true,
            Gravity.START
        )
    }

    private fun createCard(): LinearLayout {

        val card = LinearLayout(this)

        card.orientation = LinearLayout.VERTICAL
        card.setPadding(20, 18, 20, 18)
        card.setBackgroundColor(cardColor)

        return card
    }

    private fun createLabelValue(
        label: String,
        value: String
    ): TextView {

        val view = TextView(this)

        view.text = "$label\n$value"
        view.textSize = 15f
        view.setTextColor(whiteColor)
        view.setPadding(0, 8, 0, 14)

        return view
    }

    private fun createButton(
        title: String
    ): Button {

        val button = Button(this)

        button.text = title
        button.textSize = 14f
        button.setTextColor(whiteColor)

        button.layoutParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(6, 0, 6, 0)
            }

        return button
    }

    private fun createText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
        textGravity: Int
    ): TextView {

        val view = TextView(this)

        view.text = value
        view.textSize = size
        view.setTextColor(color)
        view.gravity = textGravity
        view.setPadding(0, 6, 0, 6)

        if (bold) {
            view.setTypeface(null, Typeface.BOLD)
        }

        return view
    }

    private fun addSpace(
        parent: LinearLayout,
        height: Int
    ) {

        val space = TextView(this)

        parent.addView(
            space,
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }
}
