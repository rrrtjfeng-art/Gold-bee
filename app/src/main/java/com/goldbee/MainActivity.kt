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

    private val background = Color.rgb(15, 15, 18)
    private val card = Color.rgb(27, 27, 32)
    private val white = Color.WHITE
    private val gray = Color.rgb(180, 180, 185)
    private val yellow = Color.rgb(255, 193, 7)
    private val green = Color.rgb(76, 175, 80)
    private val red = Color.rgb(244, 67, 54)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 32)
            setBackgroundColor(background)
        }

        // Header
        root.addView(
            text(
                "GOLD BEE",
                30f,
                white,
                true,
                Gravity.CENTER
            )
        )

        root.addView(
            text(
                "XAUUSD DECISION SYSTEM",
                14f,
                gray,
                false,
                Gravity.CENTER
            )
        )

        space(root, 24)

        // Market
        root.addView(section("MARKET"))

        val marketCard = cardLayout()

        marketCard.addView(labelValue("Symbol", "XAUUSD"))
        marketCard.addView(labelValue("Current Price", "---"))
        marketCard.addView(labelValue("Market Data", "NOT CONNECTED"))
        marketCard.addView(labelValue("Last Update", "---"))

        root.addView(marketCard)

        space(root, 20)

        // Timeframe
        root.addView(section("TIMEFRAME"))

        val timeframeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        timeframeRow.addView(button("H1"))
        timeframeRow.addView(button("M15"))
        timeframeRow.addView(button("M5"))

        root.addView(timeframeRow)

        space(root, 20)

        // Decision
        root.addView(section("DECISION"))

        val decisionCard = cardLayout()

        decisionCard.addView(
            text(
                "WAIT",
                34f,
                yellow,
                true,
                Gravity.CENTER
            )
        )

        decisionCard.addView(
            text(
                "NO TRADE",
                14f,
                gray,
                true,
                Gravity.CENTER
            )
        )

        root.addView(decisionCard)

        space(root, 20)

        // Trade setup
        root.addView(section("TRADE SETUP"))

        val setupCard = cardLayout()

        setupCard.addView(labelValue("Direction", "---"))
        setupCard.addView(labelValue("Entry", "---"))
        setupCard.addView(labelValue("Stop Loss", "---"))
        setupCard.addView(labelValue("Take Profit", "---"))
        setupCard.addView(labelValue("Risk : Reward", "---"))

        root.addView(setupCard)

        space(root, 20)

        // Analysis
        root.addView(section("ANALYSIS"))

        val analysisCard = cardLayout()

        analysisCard.addView(
            labelValue(
                "Trend",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            labelValue(
                "Market Structure",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            labelValue(
                "Momentum",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            labelValue(
                "Volatility",
                "WAITING FOR DATA"
            )
        )

        analysisCard.addView(
            labelValue(
                "Multi-Timeframe",
                "WAITING FOR DATA"
            )
        )

        root.addView(analysisCard)

        space(root, 20)

        // Reason
        root.addView(section("REASON"))

        val reasonCard = cardLayout()

        reasonCard.addView(
            text(
                "当前没有连接真实黄金行情。\n\n" +
                        "系统不会在没有有效市场数据的情况下产生 BUY / SELL。\n\n" +
                        "等待真实数据连接。",
                15f,
                gray,
                false,
                Gravity.START
            )
        )

        root.addView(reasonCard)

        space(root, 20)

        // Mode
        root.addView(section("MODE"))

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        modeRow.addView(button("REAL"))
        modeRow.addView(button("COPY"))

        root.addView(modeRow)

        space(root, 20)

        // Safety
        root.addView(section("EXECUTION SAFETY"))

        val safetyCard = cardLayout()

        safetyCard.addView(
            labelValue(
                "Automatic Order",
                "DISABLED"
            )
        )

        safetyCard.addView(
            labelValue(
                "User Confirmation",
                "REQUIRED"
            )
        )

        safetyCard.addView(
            labelValue(
                "MT5",
                "NOT CONNECTED"
            )
        )

        safetyCard.addView(
            text(
                "本系统不会自动下单。\n" +
                        "任何真实交易都必须经过用户明确确认。",
                14f,
                red,
                true,
                Gravity.CENTER
            )
        )

        root.addView(safetyCard)

        space(root, 30)

        root.addView(
            text(
                "GOLD BEE v1.0\nDecision Engine Foundation",
                12f,
                gray,
                false,
                Gravity.CENTER
            )
        )

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun section(title: String): TextView {
        return text(
            title,
            16f,
            yellow,
            true,
            Gravity.START
        )
    }

    private fun cardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(card)
        }
    }

    private fun labelValue(
        label: String,
        value: String
    ): TextView {
        val view = TextView(this)

        view.text = "$label\n$value"
        view.textSize = 15f
        view.setTextColor(white)
        view.setPadding(0, 8, 0, 14)

        return view
    }

    private fun button(title: String): Button {
        return Button(this).apply {
            text = title
            textSize = 14f
            setTextColor(white)
            setOnClickListener {
                // 当前版本只显示界面。
                // 后续版本会在这里接入真正的功能。
            }

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(6, 0, 6, 0)
            }
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
        gravity: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            setPadding(0, 6, 0, 6)

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun space(
        parent: LinearLayout,
        height: Int
    ) {
        val view = TextView(this)

        parent.addView(
            view,
            LinearLayout.LayoutParams(
                1,
                height
            )
        )
    }
}
