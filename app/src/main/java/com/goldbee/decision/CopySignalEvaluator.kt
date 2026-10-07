package com.goldbee.decision

import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.Trend
import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import kotlin.math.abs

data class CopySignalEvaluation(
    val action: DecisionAction,
    val signal: CopySignal,
    val reason: String,
    val priceDistance: Double?
)

object CopySignalEvaluator {

    fun evaluate(
        signal: CopySignal,
        currentPrice: Double,
        analysis: MultiTimeframeAnalysis,
        candles: Map<Timeframe, List<Candle>>
    ): CopySignalEvaluation {

        if (!signal.hasExplicitEntry()) {
            return noTrade(
                signal = signal,
                reason =
                    "COPY 信号没有明确 Entry，禁止跟单。"
            )
        }

        if (currentPrice <= 0.0) {
            return noTrade(
                signal = signal,
                reason =
                    "当前黄金价格无效。"
            )
        }

        val entry =
            signal.entry
                ?: return noTrade(
                    signal = signal,
                    reason =
                        "Entry 无效。"
                )

        val distance =
            abs(
                currentPrice - entry
            )

        val atr =
            analysis.m15.indicators.atr14

        if (atr == null || atr <= 0.0) {
            return noTrade(
                signal = signal,
                reason =
                    "M15 ATR 不足，无法判断是否追价。",
                distance = distance
            )
        }

        val maxDistance =
            atr * 0.35

        if (distance > maxDistance) {
            return noTrade(
                signal = signal,
                reason =
                    "当前价格已经远离原信号 Entry，禁止追价。",
                distance = distance
            )
        }

        val trend =
            analysis.m15.structure.trend

        if (
            signal.direction ==
            TradeDirection.BUY &&
            trend == Trend.BEARISH
        ) {
            return noTrade(
                signal = signal,
                reason =
                    "原信号 BUY，但当前 M15 已偏空，不跟。",
                distance = distance
            )
        }

        if (
            signal.direction ==
            TradeDirection.SELL &&
            trend == Trend.BULLISH
        ) {
            return noTrade(
                signal = signal,
                reason =
                    "原信号 SELL，但当前 M15 已偏多，不跟。",
                distance = distance
            )
        }

        val currentCandles =
            candles[
                Timeframe.M15
            ].orEmpty()

        if (currentCandles.size < 50) {
            return noTrade(
                signal = signal,
                reason =
                    "M15 K线不足，无法重新验证信号。",
                distance = distance
            )
        }

        return CopySignalEvaluation(
            action =
                when (signal.direction) {
                    TradeDirection.BUY ->
                        DecisionAction.BUY

                    TradeDirection.SELL ->
                        DecisionAction.SELL
                },
            signal = signal,
            reason =
                "COPY 信号通过当前行情重新验证，可以进入用户确认流程。",
            priceDistance = distance
        )
    }

    private fun noTrade(
        signal: CopySignal,
        reason: String,
        distance: Double? = null
    ): CopySignalEvaluation {

        return CopySignalEvaluation(
            action = DecisionAction.NO_TRADE,
            signal = signal,
            reason = reason,
            priceDistance = distance
        )
    }
}
