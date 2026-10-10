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

    private const val MAX_ENTRY_DISTANCE_ATR = 0.35
    private const val MAX_SPREAD_ATR_RATIO = 0.15
    private const val MIN_NET_RISK_REWARD = 1.5

    fun evaluate(
        signal: CopySignal,
        currentPrice: Double,
        analysis: MultiTimeframeAnalysis,
        candles: Map<Timeframe, List<Candle>>,
        spread: Double
    ): CopySignalEvaluation {

        if (!currentPrice.isFinite() || currentPrice <= 0.0) {
            return noTrade(signal, "当前黄金价格无效。")
        }

        if (!spread.isFinite() || spread < 0.0) {
            return noTrade(signal, "当前点差无效，禁止审核复制信号。")
        }

        if (!signal.hasExplicitEntry()) {
            return noTrade(signal, "COPY 信号没有明确 Entry，禁止追价跟随。")
        }

        if (!signal.hasStopLoss() || !signal.hasTakeProfit()) {
            return noTrade(signal, "COPY 信号必须明确提供 SL 和 TP；禁止自动猜测止损止盈。")
        }

        val entry = signal.entry
            ?: return noTrade(signal, "Entry 无效。")
        val stopLoss = signal.stopLoss
            ?: return noTrade(signal, "缺少明确止损 SL。")
        val takeProfit = signal.takeProfit
            ?: return noTrade(signal, "缺少明确止盈 TP。")

        val levelsMatchDirection = when (signal.direction) {
            TradeDirection.BUY -> stopLoss < entry && takeProfit > entry
            TradeDirection.SELL -> stopLoss > entry && takeProfit < entry
        }
        if (!levelsMatchDirection) {
            return noTrade(signal, "SL/TP 与信号方向不匹配，禁止跟随。")
        }

        if (!analysis.m15.available) {
            return noTrade(signal, "M15 数据不足，无法审核复制信号。")
        }

        val atr = analysis.m15.indicators.atr14
        if (atr == null || !atr.isFinite() || atr <= 0.0) {
            return noTrade(signal, "M15 ATR 不可用或无效，无法评估点差和追价风险。")
        }

        if (spread > atr * MAX_SPREAD_ATR_RATIO) {
            return noTrade(signal, "当前点差相对 M15 ATR 过大，禁止跟随；等待点差收窄后重新审核。")
        }

        val distance = abs(currentPrice - entry)
        if (distance > atr * MAX_ENTRY_DISTANCE_ATR) {
            return noTrade(signal, "当前价格已经远离原信号 Entry，禁止追价。", distance)
        }

        when (signal.direction) {
            TradeDirection.BUY -> {
                if (analysis.m15.structure.trend == Trend.BEARISH) {
                    return noTrade(signal, "原信号 BUY，但当前 M15 已偏空，不跟。", distance)
                }
            }
            TradeDirection.SELL -> {
                if (analysis.m15.structure.trend == Trend.BULLISH) {
                    return noTrade(signal, "原信号 SELL，但当前 M15 已偏多，不跟。", distance)
                }
            }
        }

        val currentCandles = candles[Timeframe.M15].orEmpty()
        if (currentCandles.size < 50) {
            return noTrade(signal, "M15 K 线不足 50 根，无法重新验证信号。", distance)
        }

        val stopDistance = abs(entry - stopLoss)
        val targetDistance = abs(takeProfit - entry)
        val netReward = targetDistance - spread
        val netRisk = stopDistance + spread
        if (
            !stopDistance.isFinite() ||
            !targetDistance.isFinite() ||
            !netReward.isFinite() ||
            !netRisk.isFinite() ||
            stopDistance <= 0.0 ||
            targetDistance <= 0.0 ||
            netReward <= 0.0 ||
            netRisk <= 0.0
        ) {
            return noTrade(signal, "扣除点差后，复制信号没有有效的净风险收益。", distance)
        }

        val estimatedNetRiskReward = netReward / netRisk
        if (!estimatedNetRiskReward.isFinite() || estimatedNetRiskReward < MIN_NET_RISK_REWARD) {
            return noTrade(
                signal,
                "复制信号扣除点差估算后净盈亏比仅 %.2f，低于最低要求 %.2f，拒绝跟随。"
                    .format(estimatedNetRiskReward, MIN_NET_RISK_REWARD),
                distance
            )
        }

        return CopySignalEvaluation(
            action = when (signal.direction) {
                TradeDirection.BUY -> DecisionAction.BUY
                TradeDirection.SELL -> DecisionAction.SELL
            },
            signal = signal,
            reason = "COPY 信号通过 Entry、SL/TP 方向、行情距离、点差、M15 方向和净盈亏比检查；通过审核不代表实际胜率。",
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
