package com.goldbee.decision

enum class TradeDirection {
    BUY,
    SELL
}

data class TradeSetup(
    val direction: TradeDirection,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskReward: Double,
    val reason: String
) {

    fun isValid(): Boolean {

        if (entry <= 0.0) return false
        if (stopLoss <= 0.0) return false
        if (takeProfit <= 0.0) return false
        if (riskReward <= 0.0) return false

        val risk =
            kotlin.math.abs(
                entry - stopLoss
            )

        val reward =
            kotlin.math.abs(
                takeProfit - entry
            )

        if (risk <= 0.0) return false
        if (reward <= 0.0) return false

        val calculatedRiskReward =
            reward / risk

        if (calculatedRiskReward <= 0.0) return false

        return when (direction) {

            TradeDirection.BUY ->
                stopLoss < entry &&
                        takeProfit > entry

            TradeDirection.SELL ->
                stopLoss > entry &&
                        takeProfit < entry
        }
    }
}
