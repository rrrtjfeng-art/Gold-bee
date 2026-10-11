package com.goldbee.risk

import kotlin.math.abs
import kotlin.math.floor

data class RiskBasedLotEstimate(
    val riskBudgetUsd: Double,
    val rawLots: Double,
    val lots: Double,
    val minimumLotLossUsd: Double,
    val estimatedLossUsd: Double,
    val isTradable: Boolean
)

/**
 * Conservative XAUUSD position-size estimate. Values depend on the broker's
 * actual contract size, minimum lot, lot step and round-turn commission.
 * This calculator never sends orders and is not a substitute for MT5 specs.
 */
object RiskBasedLotCalculator {
    fun calculate(
        balanceUsd: Double,
        riskPercent: Double,
        entry: Double,
        stopLoss: Double,
        contractSizeOuncesPerLot: Double,
        roundTurnCommissionPerLotUsd: Double = 0.0,
        minimumLot: Double = 0.01,
        lotStep: Double = 0.01,
        maximumLot: Double = 100.0
    ): RiskBasedLotEstimate? {
        val requiredPositive = listOf(
            balanceUsd, riskPercent, entry, stopLoss,
            contractSizeOuncesPerLot, minimumLot, lotStep, maximumLot
        )
        if (requiredPositive.any { !it.isFinite() || it <= 0.0 } ||
            !roundTurnCommissionPerLotUsd.isFinite() || roundTurnCommissionPerLotUsd < 0.0 ||
            riskPercent > 5.0 || maximumLot < minimumLot || entry == stopLoss
        ) return null

        val riskPerLot = abs(entry - stopLoss) * contractSizeOuncesPerLot +
            roundTurnCommissionPerLotUsd
        if (!riskPerLot.isFinite() || riskPerLot <= 0.0) return null

        val budget = balanceUsd * riskPercent / 100.0
        val rawLots = budget / riskPerLot
        if (!budget.isFinite() || budget <= 0.0 || !rawLots.isFinite() || rawLots <= 0.0) return null

        val minimumLoss = minimumLot * riskPerLot
        val roundedDown = floor((rawLots + lotStep * 1e-9) / lotStep) * lotStep
        val capped = minOf(roundedDown, maximumLot)
        val tradable = rawLots >= minimumLot && capped >= minimumLot
        val lots = if (tradable) capped else 0.0
        return RiskBasedLotEstimate(
            riskBudgetUsd = budget,
            rawLots = rawLots,
            lots = lots,
            minimumLotLossUsd = minimumLoss,
            estimatedLossUsd = if (tradable) lots * riskPerLot else minimumLoss,
            isTradable = tradable
        )
    }
}
