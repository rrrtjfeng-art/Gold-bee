package com.goldbee.decision

import com.goldbee.analysis.IndicatorSnapshot
import com.goldbee.analysis.MarketStructure
import com.goldbee.analysis.Trend
import com.goldbee.market.Candle

enum class DecisionAction {
    BUY,
    SELL,
    WAIT,
    NO_TRADE
}

data class DecisionResult(
    val action: DecisionAction,
    val setup: TradeSetup?,
    val confidence: Double,
    val reason: String
)

object DecisionEngine {

    fun decide(
        candles: List<Candle>,
        indicators: IndicatorSnapshot,
        structure: MarketStructure
    ): DecisionResult {

        if (candles.size < 50) {
            return noTrade(
                "K线数量不足，暂不交易。"
            )
        }

        if (!indicators.hasEnoughData()) {
            return noTrade(
                "指标数据不足，暂不交易。"
            )
        }

        val price =
            candles.last().close

        var buyScore = 0
        var sellScore = 0

        val ema9 =
            indicators.ema9 ?: return noTrade(
                "EMA9 数据不足。"
            )

        val ema20 =
            indicators.ema20 ?: return noTrade(
                "EMA20 数据不足。"
            )

        val ema50 =
            indicators.ema50 ?: return noTrade(
                "EMA50 数据不足。"
            )

        val rsi =
            indicators.rsi14 ?: return noTrade(
                "RSI 数据不足。"
            )

        val macdHistogram =
            indicators.macdHistogram
                ?: return noTrade(
                    "MACD 数据不足。"
                )

        val atr =
            indicators.atr14 ?: return noTrade(
                "ATR 数据不足。"
            )

        if (ema9 > ema20) {
            buyScore++
        } else {
            sellScore++
        }

        if (ema20 > ema50) {
            buyScore++
        } else {
            sellScore++
        }

        if (rsi > 50.0) {
            buyScore++
        } else if (rsi < 50.0) {
            sellScore++
        }

        if (macdHistogram > 0.0) {
            buyScore++
        } else if (macdHistogram < 0.0) {
            sellScore++
        }

        if (
            structure.higherHigh &&
            structure.higherLow
        ) {
            buyScore += 2
        }

        if (
            structure.lowerHigh &&
            structure.lowerLow
        ) {
            sellScore += 2
        }

        when (structure.trend) {

            Trend.BULLISH ->
                buyScore++

            Trend.BEARISH ->
                sellScore++

            Trend.SIDEWAYS ->
                Unit
        }

        if (
            buyScore < 5 &&
            sellScore < 5
        ) {
            return DecisionResult(
                action = DecisionAction.WAIT,
                setup = null,
                confidence = 0.0,
                reason = "多空条件不足，等待更清晰的结构。"
            )
        }

        if (
            buyScore >= 5 &&
            buyScore > sellScore
        ) {

            val entry =
                price

            val stopLoss =
                structure.support
                    ?.minus(atr * 0.5)
                    ?: (entry - atr * 1.5)

            val risk =
                entry - stopLoss

            if (risk <= 0.0) {
                return noTrade(
                    "BUY 风险距离无效。"
                )
            }

            val takeProfit =
                entry + risk * 2.0

            val setup =
                TradeSetup(
                    direction = TradeDirection.BUY,
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = 2.0,
                    reason =
                        "趋势偏多，EMA、RSI、MACD及市场结构共同支持 BUY。"
                )

            if (!setup.isValid()) {
                return noTrade(
                    "BUY 交易方案无效。"
                )
            }

            return DecisionResult(
                action = DecisionAction.BUY,
                setup = setup,
                confidence =
                    (buyScore / 10.0)
                        .coerceIn(0.0, 1.0),
                reason = setup.reason
            )
        }

        if (
            sellScore >= 5 &&
            sellScore > buyScore
        ) {

            val entry =
                price

            val stopLoss =
                structure.resistance
                    ?.plus(atr * 0.5)
                    ?: (entry + atr * 1.5)

            val risk =
                stopLoss - entry

            if (risk <= 0.0) {
                return noTrade(
                    "SELL 风险距离无效。"
                )
            }

            val takeProfit =
                entry - risk * 2.0

            val setup =
                TradeSetup(
                    direction = TradeDirection.SELL,
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = 2.0,
                    reason =
                        "趋势偏空，EMA、RSI、MACD及市场结构共同支持 SELL。"
                )

            if (!setup.isValid()) {
                return noTrade(
                    "SELL 交易方案无效。"
                )
            }

            return DecisionResult(
                action = DecisionAction.SELL,
                setup = setup,
                confidence =
                    (sellScore / 10.0)
                        .coerceIn(0.0, 1.0),
                reason = setup.reason
            )
        }

        return DecisionResult(
            action = DecisionAction.WAIT,
            setup = null,
            confidence = 0.0,
            reason = "多空信号冲突，等待。"
        )
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
