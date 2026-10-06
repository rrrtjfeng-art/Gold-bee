package com.goldbee

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.goldbee.analysis.IndicatorCalculator
import com.goldbee.analysis.MarketStructureAnalyzer
import com.goldbee.decision.DecisionEngine
import com.goldbee.market.MarketDataListener
import com.goldbee.market.MarketSnapshot
import com.goldbee.market.TwelveDataProvider

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var priceText: TextView
    private lateinit var decisionText: TextView
    private lateinit var detailText: TextView

    private var provider: TwelveDataProvider? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        buildScreen()
    }

    private fun buildScreen() {

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    32,
                    32,
                    32
                )
            }

        val title =
            TextView(this).apply {
                text = "GOLD BEE"
                textSize = 28f
            }

        statusText =
            TextView(this).apply {
                text = "状态：未连接"
                textSize = 16f
            }

        priceText =
            TextView(this).apply {
                text = "XAU/USD\n等待行情..."
                textSize = 22f
            }

        decisionText =
            TextView(this).apply {
                text = "决策：等待数据"
                textSize = 26f
            }

        detailText =
            TextView(this).apply {
                text = "暂无分析"
                textSize = 16f
            }

        val startButton =
            Button(this).apply {
                text = "开始真实行情"
                setOnClickListener {
                    startMarket()
                }
            }

        val stopButton =
            Button(this).apply {
                text = "停止行情"
                setOnClickListener {
                    stopMarket()
                }
            }

        root.addView(title)
        root.addView(statusText)
        root.addView(priceText)

        root.addView(
            startButton
        )

        root.addView(
            stopButton
        )

        root.addView(decisionText)
        root.addView(detailText)

        val scrollView =
            ScrollView(this).apply {
                addView(root)
            }

        setContentView(scrollView)
    }

    private fun startMarket() {

        /*
         * 暂时不把 API Key 写死进代码。
         *
         * 下一步会做设置页面，
         * 让你在手机里输入 Twelve Data API Key。
         */

        statusText.text =
            "状态：等待 API Key"

        decisionText.text =
            "决策：NO TRADE"

        detailText.text =
            "下一步加入 API Key 设置与真实行情连接。"
    }

    private fun stopMarket() {

        provider?.disconnect()

        statusText.text =
            "状态：已停止"

        decisionText.text =
            "决策：WAIT"
    }

    private fun handleSnapshot(
        snapshot: MarketSnapshot
    ) {

        val m5 =
            snapshot.candlesFor(
                com.goldbee.market.Timeframe.M5
            )

        val m15 =
            snapshot.candlesFor(
                com.goldbee.market.Timeframe.M15
            )

        val h1 =
            snapshot.candlesFor(
                com.goldbee.market.Timeframe.H1
            )

        priceText.text =
            """
            XAU/USD
            Price: %.2f
            Spread: %.2f
            """.trimIndent().format(
                snapshot.midPrice,
                snapshot.spread
            )

        if (m15.size < 50) {

            decisionText.text =
                "决策：WAIT"

            detailText.text =
                "正在等待足够的 M15 K 线..."
            
            return
        }

        val indicators =
            IndicatorCalculator.calculate(
                m15
            )

        val structure =
            MarketStructureAnalyzer.analyze(
                m15
            )

        val decision =
            DecisionEngine.decide(
                candles = m15,
                indicators = indicators,
                structure = structure
            )

        decisionText.text =
            "决策：${decision.action}"

        detailText.text =
            """
            趋势：${structure.trend}
            趋势强度：${structure.strength}
            
            EMA9：${indicators.ema9}
            EMA20：${indicators.ema20}
            EMA50：${indicators.ema50}
            EMA200：${indicators.ema200}
            
            RSI：${indicators.rsi14}
            MACD：${indicators.macd}
            MACD Signal：${indicators.macdSignal}
            MACD Histogram：${indicators.macdHistogram}
            
            ADX：${indicators.adx14}
            ATR：${indicators.atr14}
            VWAP：${indicators.vwap}
            
            支撑：${structure.support}
            阻力：${structure.resistance}
            
            原因：
            ${decision.reason}
            """.trimIndent()
    }
}
