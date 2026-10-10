package com.goldbee.analysis

import com.goldbee.decision.TradeDirection
import kotlin.math.abs

/**
 * Alternative XAUUSD take-profit levels expressed in risk multiples.
 *
 * Pip size is broker-convention dependent. The default 0.1 is only a display
 * convention for estimating pips; it must not be treated as a universal broker rule.
 */
enum class TakeProfitStyle(
    val label: String,
    val riskMultiple: Double
) {
    SMALL("小赚", 1.0),
    MEDIUM("中等", 1.5),
    LARGE("大赚", 2.5)
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
            val distance = risk * style.riskMultiple
            TakeProfitTarget(
                style = style,
                price = entry + sign * distance,
                distance = distance,
                estimatedPips = distance / pipSize,
                riskReward = style.riskMultiple
            )
        }
    }
}
