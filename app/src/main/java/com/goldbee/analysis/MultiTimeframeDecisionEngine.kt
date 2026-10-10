package com.goldbee.analysis

import com.goldbee.decision.DecisionAction
import com.goldbee.decision.DecisionResult
import com.goldbee.decision.TradeDirection
import com.goldbee.decision.TradeSetup
import com.goldbee.market.Candle

data class MultiTimeframeDecisionConfig(
    val minimumScore: Int = 6,
    val targetR: Double = 2.0
) {
    init {
        require(minimumScore in 4..9) { "minimumScore must be between 4 and 9" }
        require(targetR.isFinite() && targetR in 1.0..3.0) { "targetR must be between 1.0 and 3.0" }
    }
}

object MultiTimeframeDecisionEngine {

    fun decide(
        candles: Map<com.goldbee.market.Timeframe, List<Candle>>,
        analysis: MultiTimeframeAnalysis,
        config: MultiTimeframeDecisionConfig = MultiTimeframeDecisionConfig()
    ): DecisionResult {

        if (
            !analysis.m5.available ||
            !analysis.m15.available ||
            !analysis.h1.available
        ) {
            return noTrade(
                "M5、M15、H1 数据不足，暂不交易。"
            )
        }

        val m5 =
            analysis.m5

        val m15 =
            analysis.m15

        val h1 =
            analysis.h1

        val price =
            candles[
                com.goldbee.market.Timeframe.M5
            ]
                ?.lastOrNull()
                ?.close
                ?: candles[
                    com.goldbee.market.Timeframe.M15
                ]
                    ?.lastOrNull()
                    ?.close
                ?: return noTrade(
                    "没有有效的当前价格。"
                )

        val buyScore =
            calculateBuyScore(
                m5,
                m15,
                h1
            )

        val sellScore =
            calculateSellScore(
                m5,
                m15,
                h1
            )

        if (
            buyScore < config.minimumScore &&
            sellScore < config.minimumScore
        ) {
            return DecisionResult(
                action = DecisionAction.WAIT,
                setup = null,
                confidence = 0.0,
                reason =
                    "M5、M15、H1 的多空条件不足，等待更清晰的机会。"
            )
        }

        if (
            buyScore >= config.minimumScore &&
            buyScore > sellScore
        ) {

            val setup =
                createBuySetup(
                    price = price,
                    analysis = analysis,
                    targetR = config.targetR
                )
                    ?: return noTrade(
                        "BUY 的风险结构无效。"
                    )

            return DecisionResult(
                action = DecisionAction.BUY,
                setup = setup,
                confidence =
                    (buyScore / 10.0)
                        .coerceIn(0.0, 1.0),
                reason =
                    "M5、M15、H1 多周期偏多。"
            )
        }

        if (
            sellScore >= config.minimumScore &&
            sellScore > buyScore
        ) {

            val setup =
                createSellSetup(
                    price = price,
                    analysis = analysis,
                    targetR = config.targetR
                )
                    ?: return noTrade(
                        "SELL 的风险结构无效。"
                    )

            return DecisionResult(
                action = DecisionAction.SELL,
                setup = setup,
                confidence =
                    (sellScore / 10.0)
                        .coerceIn(0.0, 1.0),
                reason =
                    "M5、M15、H1 多周期偏空。"
            )
        }

        return DecisionResult(
            action = DecisionAction.WAIT,
            setup = null,
            confidence = 0.0,
            reason =
                "多周期方向冲突，等待。"
        )
    }

    private fun calculateBuyScore(
        m5: TimeframeAnalysis,
        m15: TimeframeAnalysis,
        h1: TimeframeAnalysis
    ): Int {

        var score = 0

        if (
            h1.structure.trend ==
            Trend.BULLISH
        ) {
            score += 3
        }

        if (
            m15.structure.trend ==
            Trend.BULLISH
        ) {
            score += 3
        }

        if (
            m5.structure.trend ==
            Trend.BULLISH
        ) {
            score += 2
        }

        if (
            m15.indicators.rsi14
                ?.let { it > 50.0 }
                == true
        ) {
            score++
        }

        if (
            m5.indicators.macdHistogram
                ?.let { it > 0.0 }
                == true
        ) {
            score++
        }

        return score
    }

    private fun calculateSellScore(
        m5: TimeframeAnalysis,
        m15: TimeframeAnalysis,
        h1: TimeframeAnalysis
    ): Int {

        var score = 0

        if (
            h1.structure.trend ==
            Trend.BEARISH
        ) {
            score += 3
        }

        if (
            m15.structure.trend ==
            Trend.BEARISH
        ) {
            score += 3
        }

        if (
            m5.structure.trend ==
            Trend.BEARISH
        ) {
            score += 2
        }

        if (
            m15.indicators.rsi14
                ?.let { it < 50.0 }
                == true
        ) {
            score++
        }

        if (
            m5.indicators.macdHistogram
                ?.let { it < 0.0 }
                == true
        ) {
            score++
        }

        return score
    }

    private fun createBuySetup(
        price: Double,
        analysis: MultiTimeframeAnalysis,
        targetR: Double
    ): TradeSetup? {

        val atr =
            analysis.m15.indicators.atr14
                ?: return null

        if (atr <= 0.0) {
            return null
        }

        val support =
            analysis.m15.structure.support

        val stopLoss =
            support
                ?.minus(atr * 0.5)
                ?: (price - atr * 1.5)

        val risk =
            price - stopLoss

        if (risk <= 0.0) {
            return null
        }

        val takeProfit =
            price + risk * targetR

        val setup =
            TradeSetup(
                direction = TradeDirection.BUY,
                entry = price,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                riskReward = targetR,
                reason =
                    "H1、M15、M5 多周期偏多，BUY 条件达到最低要求。"
            )

        return if (setup.isValid()) {
            setup
        } else {
            null
        }
    }

    private fun createSellSetup(
        price: Double,
        analysis: MultiTimeframeAnalysis,
        targetR: Double
    ): TradeSetup? {

        val atr =
            analysis.m15.indicators.atr14
                ?: return null

        if (atr <= 0.0) {
            return null
        }

        val resistance =
            analysis.m15.structure.resistance

        val stopLoss =
            resistance
                ?.plus(atr * 0.5)
                ?: (price + atr * 1.5)

        val risk =
            stopLoss - price

        if (risk <= 0.0) {
            return null
        }

        val takeProfit =
            price - risk * targetR

        val setup =
            TradeSetup(
                direction = TradeDirection.SELL,
                entry = price,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                riskReward = targetR,
                reason =
                    "H1、M15、M5 多周期偏空，SELL 条件达到最低要求。"
            )

        return if (setup.isValid()) {
            setup
        } else {
            null
        }
    }

    private fun noTrade(
        reason: String
    ): DecisionResult {

        return DecisionResult(
            action = DecisionAction.NO_TRADE,
            setup = null,
            confidence = 0.0,
            reason = reason
        )
    }
}
