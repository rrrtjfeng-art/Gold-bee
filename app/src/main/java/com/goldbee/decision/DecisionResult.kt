package com.goldbee.decision

enum class DecisionAction {
BUY,
SELL,
WAIT,
NO_TRADE
}

data class DecisionResult(
val action: DecisionAction,
val setup: TradeSetup?,
val confidence: Double,
val reason: String
)
