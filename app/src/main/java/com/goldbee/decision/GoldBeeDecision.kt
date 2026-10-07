package com.goldbee.decision

data class GoldBeeDecision(
    val mode: DecisionMode,
    val action: DecisionAction,
    val setup: TradeSetup?,
    val confidence: Double,
    val reason: String,
    val currentPrice: Double,
    val timestamp: Long = System.currentTimeMillis()
) {

    fun canConfirm(): Boolean {
        return (
            action == DecisionAction.BUY ||
            action == DecisionAction.SELL
        ) &&
                setup != null &&
                setup.isValid()
    }

    fun isNoTrade(): Boolean {
        return action == DecisionAction.NO_TRADE
    }

    fun isWaiting(): Boolean {
        return action == DecisionAction.WAIT
    }
}
