package com.goldbee.execution

import com.goldbee.decision.TradeDirection

enum class TradeResult {
    OPEN,
    WIN,
    LOSS,
    CANCELLED
}

data class TradeRecord(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val volume: Double,
    val result: TradeResult,
    val profitLoss: Double,
    val openedAt: Long,
    val closedAt: Long? = null
) {

    fun isClosed(): Boolean {
        return result == TradeResult.WIN ||
                result == TradeResult.LOSS ||
                result == TradeResult.CANCELLED
    }

    fun isValid(): Boolean {

        if (id.isBlank()) {
            return false
        }

        if (symbol.isBlank()) {
            return false
        }

        if (entryPrice <= 0.0) {
            return false
        }

        if (stopLoss <= 0.0) {
            return false
        }

        if (takeProfit <= 0.0) {
            return false
        }

        if (volume <= 0.0) {
            return false
        }

        return when (direction) {

            TradeDirection.BUY ->
                stopLoss < entryPrice &&
                        takeProfit > entryPrice

            TradeDirection.SELL ->
                stopLoss > entryPrice &&
                        takeProfit < entryPrice
        }
    }
}
