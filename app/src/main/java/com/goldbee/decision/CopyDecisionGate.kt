package com.goldbee.decision

import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.Trend
import com.goldbee.market.MarketSnapshot
import kotlin.math.abs

data class CopyDecisionResult(
    val decision: DecisionResult,
    val followable: Boolean,
    val reason: String
)

object CopyDecisionGate {

    private const val MAX_ENTRY_DISTANCE_ATR = 0.35

    fun evaluate(
        signal: CopySignal,
        snapshot: MarketSnapshot,
        analysis: MultiTimeframeAnalysis
    ): CopyDecisionResult {

        if (!snapshot.isPriceValid()) {
            return blocked("当前行情价格无效。")
        }

        if (!snapshot.isFresh()) {
            return blocked("当前行情已经过期，禁止跟随。")
        }

        if (!signal.hasExplicitEntry()) {
            return blocked("复制信号没有明确 Entry，禁止追价跟随。")
        }

        if (!signal.hasStopLoss() || !signal.hasTakeProfit()) {
            return blocked("复制信号必须明确提供 SL 和 TP；禁止自动猜测止损止盈。")
        }

        if (!analysis.m15.available) {
            return blocked("M15 数据不足，无法审核复制信号。")
        }

        val entry =
            signal.entry
                ?: return blocked("Entry 无效。")

        val currentPrice =
            snapshot.midPrice

        val atr =
            analysis.m15.indicators.atr14
                ?: return blocked("M15 ATR 不可用。")

        if (atr <= 0.0) {
            return blocked("ATR 无效。")
        }

        val maxDistance =
            atr * MAX_ENTRY_DISTANCE_ATR

        val distance =
            abs(currentPrice - entry)

        if (distance > maxDistance) {
            return blocked(
                "当前价格距离信号 Entry 太远，禁止追价。"
            )
        }

        when (signal.direction) {

            TradeDirection.BUY -> {

                if (
                    analysis.m15.structure.trend ==
                    Trend.BEARISH
                ) {
                    return blocked(
                        "当前 M15 偏空，原 BUY 信号不再适合跟随。"
                    )
                }
            }

            TradeDirection.SELL -> {

                if (
                    analysis.m15.structure.trend ==
                    Trend.BULLISH
                ) {
                    return blocked(
                        "当前 M15 偏多，原 SELL 信号不再适合跟随。"
                    )
                }
            }
        }

        val setup =
            createSetup(
                signal = signal,
                currentPrice = currentPrice
            )

        if (!setup.isValid()) {
            return blocked("复制信号的交易方案无效。")
        }

        val reason =
            "复制信号通过 Entry、行情新鲜度、ATR 距离和 M15 方向检查，可以进入用户确认阶段。"

        return CopyDecisionResult(
            decision = DecisionResult(
                action =
                    when (signal.direction) {
                        TradeDirection.BUY ->
                            DecisionAction.BUY

                        TradeDirection.SELL ->
                            DecisionAction.SELL
                    },
                setup = setup,
                confidence = 1.0,
                reason = reason
            ),
            followable = true,
            reason = reason
        )
    }

    private fun createSetup(
        signal: CopySignal,
        currentPrice: Double
    ): TradeSetup {

        val entry =
            signal.entry
                ?: currentPrice

        val stopLoss =
            requireNotNull(signal.stopLoss) {
                "COPY signal must include an explicit stop loss."
            }

        val takeProfit =
            requireNotNull(signal.takeProfit) {
                "COPY signal must include an explicit take profit."
            }

        val risk =
            abs(entry - stopLoss)

        val reward =
            abs(takeProfit - entry)

        val riskReward =
            if (risk > 0.0) {
                reward / risk
            } else {
                0.0
            }

        return TradeSetup(
            direction = signal.direction,
            entry = entry,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskReward = riskReward,
            reason = "复制信号经过 Gold Bee 当前行情重新审核。"
        )
    }

    private fun blocked(
        reason: String
    ): CopyDecisionResult {

        return CopyDecisionResult(
            decision = DecisionResult(
                action = DecisionAction.NO_TRADE,
                setup = null,
                confidence = 0.0,
                reason = reason
            ),
            followable = false,
            reason = reason
        )
    }
}
