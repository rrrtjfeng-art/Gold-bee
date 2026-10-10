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

        val preflight = TradePreflight.check(
            TradePreflightRequest(
                currentPrice = snapshot.midPrice,
                setup = setup,
                atr = atr,
                riskReward = setup.riskReward,
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
