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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        root.addView(createSection("MARKET"))

        val marketCard = createCard()

        marketCard.addView(createLabelValue("Symbol", "XAUUSD"))
        marketCard.addView(createLabelValue("Current Price", "---"))
        marketCard.addView(createLabelValue("Market Data", "NOT CONNECTED"))
        marketCard.addView(createLabelValue("Last Update", "---"))

        root.addView(marketCard)

        addSpace(root, 20)

        root.addView(createSection("TIMEFRAME"))

        val timeframeRow = LinearLayout(this)
        timeframeRow.orientation = LinearLayout.HORIZONTAL
        timeframeRow.gravity = Gravity.CENTER

        timeframeRow.addView(createButton("H1"))
        timeframeRow.addView(createButton("M15"))
        timeframeRow.addView(createButton("M5"))

        root.addView(timeframeRow)

        addSpace(root, 20)

        root.addView(createSection("DECISION"))

        val decisionCard = createCard()

        decisionCard.addView(
            createText(
                "WAIT",
                34f,
                yellowColor,
                true,
                Gravity.CENTER
            )
        )

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

        root.addView(createSection("TRADE SETUP"))

        val setupCard = createCard()

        setupCard.addView(createLabelValue("Direction", "---"))
        setupCard.addView(createLabelValue("Entry", "---"))
        setupCard.addView(createLabelValue("Stop Loss", "---"))
        setupCard.addView(createLabelValue("Take Profit", "---"))
        setupCard.addView(createLabelValue("Risk : Reward", "---"))

        root.addView(setupCard)

        addSpace(root, 20)

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

        root.addView(createSection("REASON"))

        val reasonCard = createCard()

        reasonCard.addView(
            createText(
                "当前没有连接真实黄金行情。\n\n" +
                        "系统不会在没有有效市场数据的情况下产生 BUY / SELL。\n\n" +
                        "等待真实数据连接。",
                15f,
                grayColor,
                false,
                Gravity.START
            )
        )

        root.addView(reasonCard)

        addSpace(root, 20)

        root.addView(createSection("MODE"))

        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        modeRow.gravity = Gravity.CENTER

        modeRow.addView(createButton("REAL"))
        modeRow.addView(createButton("COPY"))

        root.addView(modeRow)

        addSpace(root, 20)

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
                "GOLD BEE v1.0\nDecision Engine Foundation",
                12f,
                grayColor,
                false,
                Gravity.CENTER
            )
        )

        scrollView.addView(root)

        setContentView(scrollView)
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

    private fun createButton(title: String): Button {
        val button = Button(this)

        button.text = title
        button.textSize = 14f
        button.setTextColor(whiteColor)

        button.setOnClickListener {
            // 后续版本接入实际功能
        }

        button.layoutParams = LinearLayout.LayoutParams(
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
