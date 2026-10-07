package com.goldbee.risk

import com.goldbee.decision.TradeSetup

data class TradePreflightRequest(
    val currentPrice: Double,
    val setup: TradeSetup,
    val atr: Double,
    val riskReward: Double,
    val limits: RiskLimits,
    val state: RiskState
)

data class TradePreflightResult(
    val allowed: Boolean,
    val reason: String,
    val entryValidation: EntryValidationResult?
)

object TradePreflight {

    fun check(
        request: TradePreflightRequest
    ): TradePreflightResult {

        if (!request.setup.isValid()) {
            return blocked(
                "交易方案无效。"
            )
        }

        val entryValidation =
            EntryPriceGuard.validate(
                currentPrice =
                    request.currentPrice,
                setup =
                    request.setup,
                atr =
                    request.atr
            )

        if (
            entryValidation.status !=
            EntryValidationStatus.VALID
        ) {
            return TradePreflightResult(
                allowed = false,
                reason =
                    entryValidation.reason,
                entryValidation =
                    entryValidation
            )
        }

        val riskAllowed =
            RiskLimitChecker.canTrade(
                limits =
                    request.limits,
                state =
                    request.state,
                riskReward =
                    request.riskReward
            )

        if (!riskAllowed) {
            return TradePreflightResult(
                allowed = false,
                reason =
                    "风险限制不允许这笔交易。",
                entryValidation =
                    entryValidation
            )
        }

        return TradePreflightResult(
            allowed = true,
            reason =
                "交易通过预检查，等待用户确认。",
            entryValidation =
                entryValidation
        )
    }

    private fun blocked(
        reason: String
    ): TradePreflightResult {

        return TradePreflightResult(
            allowed = false,
            reason = reason,
            entryValidation = null
        )
    }
}
