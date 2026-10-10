package com.goldbee.decision

import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.MultiTimeframeDecisionEngine
import com.goldbee.market.Candle
import com.goldbee.market.MarketFreshnessGuard
import com.goldbee.market.MarketSnapshot
import com.goldbee.market.Timeframe
import com.goldbee.risk.RiskLimits
import com.goldbee.risk.RiskState
import com.goldbee.risk.TradePreflight
import com.goldbee.risk.TradePreflightRequest
import kotlin.math.abs

data class TradeDecisionGateResult(
    val decision: DecisionResult,
    val preflightPassed: Boolean,
    val reason: String
)

object TradeDecisionGate {

    private const val MAX_SPREAD_ATR_RATIO = 0.15
    private val freshnessGuard = MarketFreshnessGuard(maxAgeMillis = 3_000L)

    fun evaluate(
        snapshot: MarketSnapshot,
        analysis: MultiTimeframeAnalysis,
        candles: Map<Timeframe, List<Candle>>,
        limits: RiskLimits = RiskLimits(),
        riskState: RiskState = RiskState()
    ): TradeDecisionGateResult {

        if (!snapshot.isPriceValid()) {
            return blocked("当前买卖报价无效，禁止交易。")
        }

        if (snapshot.source.contains("Twelve Data", ignoreCase = true)) {
            return blocked(
                "当前行情源只提供参考价，没有真实 bid/ask 点差；无法可靠评估净盈亏比，禁止生成可执行交易建议。请使用具备真实买卖报价的行情源。"
            )
        }

        if (!freshnessGuard.isFresh(snapshot)) {
            return blocked("行情已经过期，禁止交易。")
        }

        val atr = analysis.m15.indicators.atr14
            ?: return blocked("M15 ATR 不可用，无法评估点差和风险。")

        if (!atr.isFinite() || atr <= 0.0) {
            return blocked("M15 ATR 无效，禁止生成交易方案。")
        }

        if (snapshot.spread > atr * MAX_SPREAD_ATR_RATIO) {
            return blocked("当前点差相对 M15 ATR 过大，禁止进场；等待点差收窄后重新刷新报价。")
        }

        val decision = MultiTimeframeDecisionEngine.decide(
            candles = candles,
            analysis = analysis
        )

        if (
            decision.action != DecisionAction.BUY &&
            decision.action != DecisionAction.SELL
        ) {
            return TradeDecisionGateResult(
                decision = decision,
                preflightPassed = false,
                reason = decision.reason
            )
        }

        val setup = decision.setup
            ?: return blocked("交易方案不存在。")

        // Estimate the impact of one spread on both the loss and profit sides.
        // A gross 2R setup can be materially worse after execution costs.
        val stopDistance = abs(setup.entry - setup.stopLoss)
        val targetDistance = abs(setup.takeProfit - setup.entry)
        val netReward = targetDistance - snapshot.spread
        val netRisk = stopDistance + snapshot.spread

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
            return blocked("扣除点差成本后，交易方案的净风险收益无效。")
        }

        val estimatedNetRiskReward = netReward / netRisk
        if (
            !estimatedNetRiskReward.isFinite() ||
            estimatedNetRiskReward < limits.minimumRiskReward
        ) {
            return blocked(
                "扣除点差估算后净盈亏比仅 %.2f，低于最低要求 %.2f；不为追求交易次数而放宽标准。"
                    .format(estimatedNetRiskReward, limits.minimumRiskReward)
            )
        }

        val preflight = TradePreflight.check(
            TradePreflightRequest(
                currentPrice = snapshot.midPrice,
                setup = setup,
                atr = atr,
                riskReward = estimatedNetRiskReward,
                limits = limits,
                state = riskState
            )
        )

        if (!preflight.allowed) {
            return TradeDecisionGateResult(
                decision = DecisionResult(
                    action = DecisionAction.NO_TRADE,
                    setup = null,
                    confidence = 0.0,
                    reason = preflight.reason
                ),
                preflightPassed = false,
                reason = preflight.reason
            )
        }

        return TradeDecisionGateResult(
            decision = decision,
            preflightPassed = true,
            reason = "交易通过行情、入场价格和风险预检查，等待用户确认。"
        )
    }

    private fun blocked(
        reason: String
    ): TradeDecisionGateResult {
        return TradeDecisionGateResult(
            decision = DecisionResult(
                action = DecisionAction.NO_TRADE,
                setup = null,
                confidence = 0.0,
                reason = reason
            ),
            preflightPassed = false,
            reason = reason
        )
    }
}
