package com.goldbee.execution

import com.goldbee.decision.TradeDirection

data class Mt5OrderRequest(
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val createdAt: Long
) {

    fun isValid(): Boolean {

        if (symbol.isBlank()) {
            return false
        }

        if (volume <= 0.0) {
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
