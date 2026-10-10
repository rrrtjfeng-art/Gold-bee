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
    private const val MAX_SPREAD_ATR_RATIO = 0.15
    private const val MIN_NET_RISK_REWARD = 1.5

    fun evaluate(
        signal: CopySignal,
        snapshot: MarketSnapshot,
        analysis: MultiTimeframeAnalysis
    ): CopyDecisionResult {

        if (!snapshot.isPriceValid()) {
            return blocked("当前行情价格无效。")
        }

        val normalizedSymbol = snapshot.symbol.uppercase().replace("/", "").trim()
        if (!normalizedSymbol.contains("XAUUSD") && normalizedSymbol != "GOLD") {
            return blocked("当前行情品种不是可识别的黄金品种，禁止审核复制信号。")
        }

        if (snapshot.source.contains("Twelve Data", ignoreCase = true)) {
            return blocked(
                "当前行情源只有参考价，没有真实 bid/ask 点差；无法评估复制信号的净盈亏比，禁止跟随。请使用具备真实买卖报价的行情源。"
            )
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

        // BUY enters on Ask; SELL enters on Bid. Keep the original signal Entry
        // solely as the anti-chasing reference.
        val currentPrice = when (signal.direction) {
            TradeDirection.BUY -> snapshot.ask
            TradeDirection.SELL -> snapshot.bid
        }

        val stopLoss = signal.stopLoss
            ?: return blocked("缺少明确止损 SL。")
        val takeProfit = signal.takeProfit
            ?: return blocked("缺少明确止盈 TP。")
        val levelsMatchDirection = when (signal.direction) {
            TradeDirection.BUY -> stopLoss < entry && takeProfit > entry
            TradeDirection.SELL -> stopLoss > entry && takeProfit < entry
        }
        if (!levelsMatchDirection) {
            return blocked("SL/TP 与信号方向不匹配，禁止跟随。")
        }
        val alreadyResolved = when (signal.direction) {
            TradeDirection.BUY -> when {
                snapshot.bid <= stopLoss -> "当前 Bid 已到达或跌破原信号 SL，信号失效。"
                snapshot.bid >= takeProfit -> "当前 Bid 已到达或超过原信号 TP，禁止重复追入。"
                else -> null
            }
            TradeDirection.SELL -> when {
                snapshot.ask >= stopLoss -> "当前 Ask 已到达或超过原信号 SL，信号失效。"
                snapshot.ask <= takeProfit -> "当前 Ask 已到达或跌破原信号 TP，禁止重复追入。"
                else -> null
            }
        }
        if (alreadyResolved != null) return blocked(alreadyResolved)

        val atr =
            analysis.m15.indicators.atr14
                ?: return blocked("M15 ATR 不可用。")

        if (!atr.isFinite() || atr <= 0.0) {
            return blocked("ATR 无效，禁止跟随复制信号。")
        }

        if (snapshot.spread > atr * MAX_SPREAD_ATR_RATIO) {
            return blocked("当前点差相对 M15 ATR 过大，禁止跟随；等待点差收窄后重新审核。")
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

        // Entry is the executable side (Ask for BUY, Bid for SELL), so spread
        // is already reflected in the entry price. Do not count it twice.
        // Spread is separately gated against ATR above.
        val stopDistance = abs(setup.entry - setup.stopLoss)
        val targetDistance = abs(setup.takeProfit - setup.entry)
        val netReward = targetDistance
        val netRisk = stopDistance

        if (
            !netReward.isFinite() ||
            !netRisk.isFinite() ||
            netReward <= 0.0 ||
            netRisk <= 0.0
        ) {
            return blocked("扣除点差后，复制信号没有有效的净风险收益。")
        }

        val estimatedNetRiskReward = netReward / netRisk
        if (
            !estimatedNetRiskReward.isFinite() ||
            estimatedNetRiskReward < MIN_NET_RISK_REWARD
        ) {
            return blocked(
                "复制信号扣除点差估算后净盈亏比仅 %.2f，低于最低要求 %.2f，拒绝跟随。"
                    .format(estimatedNetRiskReward, MIN_NET_RISK_REWARD)
            )
        }

        val reason =
            "复制信号通过原 Entry 追价距离、当前可执行报价、SL/TP 有效性、行情新鲜度、点差和 M15 方向检查；仍需人工确认，不代表实际胜率。"

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
                confidence = 0.0,
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

        val entry = currentPrice

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
