package com.goldbee.analysis

import com.goldbee.decision.TradeDirection
import kotlin.math.abs

/**
 * Fixed gold-price-distance TP examples. These are candidate levels, not
 * predictions; market structure, spread, volatility and execution still matter.
 *
 * XAUUSD pip conventions vary by broker. The default is only a display estimate.
 */
enum class TakeProfitStyle(
    val label: String,
    val priceDistance: Double
) {
    SMALL("小赚", 2.0),
    MEDIUM("中赚", 5.0),
    LARGE("大赚", 10.0)
}

data class TakeProfitTarget(
    val style: TakeProfitStyle,
    val price: Double,
    val distance: Double,
    val estimatedPips: Double,
    val riskReward: Double
)

object TakeProfitPlanner {
    const val DEFAULT_XAUUSD_PIP_SIZE: Double = 0.1

    fun targets(
        direction: TradeDirection,
        entry: Double,
        stopLoss: Double,
        pipSize: Double = DEFAULT_XAUUSD_PIP_SIZE
    ): List<TakeProfitTarget> {
        require(entry.isFinite() && entry > 0.0) { "Entry must be finite and positive" }
        require(stopLoss.isFinite() && stopLoss > 0.0) { "Stop loss must be finite and positive" }
        require(pipSize.isFinite() && pipSize > 0.0) { "Pip size must be finite and positive" }

        val risk = abs(entry - stopLoss)
        require(risk > 0.0) { "Entry and stop loss must differ" }
        require(
            (direction == TradeDirection.BUY && stopLoss < entry) ||
                (direction == TradeDirection.SELL && stopLoss > entry)
        ) { "Stop loss is on the wrong side of entry" }

        val sign = if (direction == TradeDirection.BUY) 1.0 else -1.0
        return TakeProfitStyle.entries.map { style ->
            val distance = style.priceDistance
            TakeProfitTarget(
                style = style,
                price = entry + sign * distance,
                distance = distance,
                estimatedPips = distance / pipSize,
                riskReward = distance / risk
            )
        }
    }
}
