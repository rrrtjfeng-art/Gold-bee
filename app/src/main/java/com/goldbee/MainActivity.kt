package com.goldbee

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val bg = Color.rgb(9, 12, 18)
    private val panel = Color.rgb(19, 24, 34)
    private val panel2 = Color.rgb(25, 31, 43)
    private val gold = Color.rgb(255, 193, 7)
    private val white = Color.rgb(240, 243, 250)
    private val muted = Color.rgb(145, 155, 173)
    private val green = Color.rgb(58, 210, 145)
    private val red = Color.rgb(255, 103, 112)

    private lateinit var statusText: TextView
    private lateinit var decisionText: TextView
    private lateinit var modeText: TextView
    private lateinit var copyInput: EditText
    private lateinit var copyPanel: LinearLayout

    private var currentMode = "REAL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        buildScreen()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int = 16,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun text(
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    }

    private fun vertical(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

    private fun horizontal(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false,
        bottom: Int = 8
    ): TextView {
        val view = text(value, size, color, bold)
        parent.addView(
            view,
            LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                bottomMargin = dp(bottom)
            }
        )
        return view
    }

    private fun card(): LinearLayout =
        vertical().apply {
            background = rounded(panel, 18)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                -1,
                -2
            ).apply {
                bottomMargin = dp(14)
            }
        }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        onClick: () -> Unit,
        primary: Boolean = false
    ): Button {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(if (primary) bg else white)
            background = rounded(
                if (primary) gold else panel2,
                12
            )
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { onClick() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                marginEnd = dp(8)
            }
        )
        return button
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }

        val root = vertical().apply {
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }

        scroll.addView(root)
        setContentView(scroll)

        // Header
        val header = horizontal()

        val brand = vertical()
        brand.addView(text("GOLD BEE", 25f, gold, true))
        brand.addView(
            text(
                "XAUUSD ANALYSIS TERMINAL",
                10f,
                muted,
                true
            )
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val version = text("MOBILE", 11f, muted, true).apply {
            background = rounded(panel2, 10)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(version)
        root.addView(header)

        addText(
            root,
            "你的黄金市场分析工作台",
            13f,
            muted,
            false,
            18
        )

        // Connection status
        val connection = card()
        val connectionRow = horizontal()

        val dot = text("●", 12f, gold, true)
        connectionRow.addView(dot)

        statusText = text("行情尚未连接", 14f, white, true)
        connectionRow.addView(
            statusText,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(8)
            }
        )

        connection.addView(connectionRow)
        addText(
            connection,
            "当前没有真实行情数据。交易分析将在有效数据接入后启用。",
            12f,
            muted,
            false,
            12
        )

        val connectionButtons = horizontal()
        addButton(
            connectionButtons,
            "连接行情",
            {
                statusText.text = "等待配置行情源"
                decisionText.text = "NO TRADE"
                modeText.text =
                    "尚未连接真实行情，暂不生成交易建议。"
            },
            true
        )
        addButton(
            connectionButtons,
            "停止",
            {
                statusText.text = "行情已停止"
                decisionText.text = "NO TRADE"
                modeText.text = "行情已停止，等待重新连接。"
            }
        )
        connection.addView(connectionButtons)
        root.addView(connection)

        // Market quote
        val quote = card()
        addText(quote, "黄金行情 / GOLD SPOT", 12f, muted, true, 12)
        addText(quote, "XAU / USD", 14f, white, true, 2)
        addText(quote, "等待真实报价", 28f, gold, true, 4)
        addText(quote, "买价  —       卖价  —", 13f, muted, false, 4)
        addText(quote, "点差  —       更新时间  —", 12f, muted, false, 0)
        root.addView(quote)

        // REAL / COPY modes
        val modeCard = card()
        addText(modeCard, "分析模式", 16f, white, true, 12)

        val modes = horizontal()
        addButton(
            modes,
            "REAL · 实时分析",
            {
                currentMode = "REAL"
                updateMode()
            },
            true
        )
        addButton(
            modes,
            "COPY · 信号复核",
            {
                currentMode = "COPY"
                updateMode()
            }
        )
        modeCard.addView(modes)

        modeText = addText(
            modeCard,
            "REAL：根据真实市场数据评估行情。当前行情未接通。",
            12f,
            muted,
            false,
            0
        )
        root.addView(modeCard)

        // Chart
        val chart = card()
        addText(chart, "价格结构 / PRICE ACTION", 16f, white, true, 4)
        addText(
            chart,
            "K 线图区域 · 等待真实历史 K 线",
            12f,
            muted,
            false,
            10
        )

        chart.addView(
            ChartPlaceholder(this),
            LinearLayout.LayoutParams(-1, dp(190))
        )

        addText(
            chart,
            "未接入真实 K 线，不显示模拟价格或虚构走势。",
            11f,
            muted,
            false,
            0
        )
        root.addView(chart)

        // Indicators
        val indicators = card()
        addText(indicators, "技术指标", 16f, white, true, 14)

        addMetric(indicators, "EMA 9 / 20 / 50 / 200", "—")
        addMetric(indicators, "RSI 14", "—")
        addMetric(indicators, "MACD / Signal / Histogram", "—")
        addMetric(indicators, "ADX 14 / ATR 14", "—")
        addMetric(indicators, "VWAP", "—")
        root.addView(indicators)

        // Structure
        val structure = card()
        addText(structure, "市场结构", 16f, white, true, 14)
        addMetric(structure, "趋势方向", "未知")
        addMetric(structure, "趋势强度", "—")
        addMetric(structure, "支撑区域", "—")
        addMetric(structure, "阻力区域", "—")
        root.addView(structure)

        // Decision
        val decision = card()
        addText(decision, "交易决策 / TRADE PLAN", 16f, white, true, 10)

        decisionText = text("NO TRADE", 26f, red, true).apply {
            background = rounded(panel2, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        decision.addView(
            decisionText,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        )

        addMetric(decision, "Entry · 入场", "—")
        addMetric(decision, "SL · 止损", "—")
        addMetric(decision, "TP · 止盈", "—")
        addMetric(decision, "Risk : Reward · 风险回报比", "—")
        addMetric(decision, "仓位风险", "未计算")

        addText(decision, "判断理由", 13f, gold, true, 5)
        addText(
            decision,
            "当前没有经过验证的实时行情，因此不会给出虚构的买卖方向、入场价或止损止盈。",
            12f,
            muted,
            false,
            0
        )
        root.addView(decision)

        // Copy signal input
        copyPanel = card()
        addText(copyPanel, "COPY 信号复核", 16f, white, true, 6)
        addText(
            copyPanel,
            "粘贴别人提供的黄金信号。接入行情后，再检查方向、入场价与当前价格是否仍然匹配。",
            12f,
            muted,
            false,
            10
        )

        copyInput = EditText(this).apply {
            hint = "例如：XAUUSD BUY Entry 4000 SL 3990 TP 4020"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 13f
            gravity = Gravity.TOP
            minLines = 3
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel2, 12)
        }

        copyPanel.addView(
            copyInput,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )

        val reviewButton = Button(this).apply {
            text = "复核信号"
            isAllCaps = false
            setTextColor(bg)
            background = rounded(gold, 12)
            setOnClickListener {
                val signal = copyInput.text.toString().trim()

                modeText.text = "COPY：已提交信号复核请求。"

                if (signal.isEmpty()) {
                    decisionText.text = "NO TRADE"
                    statusText.text = "请先粘贴信号"
                } else {
                    decisionText.text = "NO TRADE"
                    statusText.text = "等待行情源接入"
                    modeText.text =
                        "已填写信号，但尚无真实行情可验证其入场有效性。禁止盲目跟单。"
                }
            }
        }

        copyPanel.addView(
            reviewButton,
            LinearLayout.LayoutParams(-1, dp(48))
        )
        root.addView(copyPanel)
        copyPanel.visibility = View.GONE

        // Safety footer
        val footer = vertical().apply {
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }

        addText(
            footer,
            "执行规则",
            13f,
            gold,
            true,
            5
        )
        addText(
            footer,
            "Gold Bee 负责分析与风险提示，不自动下单。所有真实订单由你在 MT5 中自行确认。",
            12f,
            muted,
            false,
            5
        )
        addText(
            footer,
            "当前版本：主界面框架 · 实时行情尚未接通",
            10f,
            muted,
            false,
            0
        )
        root.addView(footer)
    }

    private fun addMetric(
        parent: LinearLayout,
        label: String,
        value: String
    ) {
        val row = horizontal().apply {
            setPadding(0, dp(8), 0, dp(8))
        }

        row.addView(
            text(label, 12f, muted),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        row.addView(
            text(value, 12f, white, true).apply {
                gravity = Gravity.END
            }
        )

        parent.addView(row)
        parent.addView(
            View(this).apply {
                setBackgroundColor(Color.rgb(39, 46, 59))
            },
            LinearLayout.LayoutParams(-1, dp(1))
        )
    }

    private fun updateMode() {
        if (currentMode == "REAL") {
            copyPanel.visibility = View.GONE
            modeText.text =
                "REAL：根据真实市场数据评估行情。当前行情未接通。"
        } else {
            copyPanel.visibility = View.VISIBLE
            modeText.text =
                "COPY：复核外部信号。没有实时行情和明确入场价时，不跟单。"
        }
    }

    private inner class ChartPlaceholder(
        context: android.content.Context
    ) : View(context) {

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 44, 57)
            strokeWidth = dp(1).toFloat()
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = dp(12).toFloat()
            textAlign = Paint.Align.CENTER
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            for (i in 1..4) {
                val y = h * i / 5f
                canvas.drawLine(0f, y, w, y, gridPaint)
            }

            for (i in 1..5) {
                val x = w * i / 6f
                canvas.drawLine(x, 0f, x, h, gridPaint)
            }

            canvas.drawText(
                "等待真实行情数据",
                w / 2f,
                h / 2f,
                labelPaint
            )
        }
    }
}
