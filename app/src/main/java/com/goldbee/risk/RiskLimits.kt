package com.goldbee.risk

data class RiskLimits(
    val maxRiskPercentPerTrade: Double = 1.0,
    val maxDailyLossPercent: Double = 3.0,
    val maxConsecutiveLosses: Int = 3,
    val minimumRiskReward: Double = 1.5
) {

    fun isValid(): Boolean {

        if (
            maxRiskPercentPerTrade <= 0.0 ||
            maxRiskPercentPerTrade > 100.0
        ) {
            return false
        }

        if (
            maxDailyLossPercent <= 0.0 ||
            maxDailyLossPercent > 100.0
        ) {
            return false
        }

        if (
            maxConsecutiveLosses <= 0
        ) {
            return false
        }

        if (
            minimumRiskReward <= 0.0
        ) {
            return false
        }

        return true
    }
}

data class RiskState(
    val dailyLossPercent: Double = 0.0,
    val consecutiveLosses: Int = 0
)

object RiskLimitChecker {

    fun canTrade(
        limits: RiskLimits,
        state: RiskState,
        riskReward: Double
    ): Boolean {

        if (!limits.isValid()) {
            return false
        }

        if (
            state.dailyLossPercent >=
            limits.maxDailyLossPercent
        ) {
            return false
        }

        if (
            state.consecutiveLosses >=
            limits.maxConsecutiveLosses
        ) {
            return false
        }

        if (
            riskReward <
            limits.minimumRiskReward
        ) {
            return false
        }

        return true
    }
}
