package com.goldbee.risk

import com.goldbee.decision.TradeSetup
import kotlin.math.abs

data class RiskParameters(
    val riskAmount: Double,
    val riskPercent: Double,
    val stopLossDistance: Double,
    val takeProfitDistance: Double,
    val riskReward: Double,
    val suggestedLotSize: Double
)

object RiskManager {

    fun calculate(
        balance: Double,
        riskPercent: Double,
        setup: TradeSetup,
        pricePerLot: Double = 100.0
    ): RiskParameters? {
        if (!balance.isFinite() || balance <= 0.0) return null
        if (!riskPercent.isFinite() || riskPercent <= 0.0 || riskPercent > 100.0) return null
        if (!pricePerLot.isFinite() || pricePerLot <= 0.0) return null
        if (!setup.isValid()) return null

        val riskAmount = balance * riskPercent / 100.0
        val stopLossDistance = abs(setup.entry - setup.stopLoss)
        val takeProfitDistance = abs(setup.takeProfit - setup.entry)

        if (!riskAmount.isFinite() || riskAmount <= 0.0) return null
        if (!stopLossDistance.isFinite() || stopLossDistance <= 0.0) return null
        if (!takeProfitDistance.isFinite() || takeProfitDistance <= 0.0) return null

        val riskReward = takeProfitDistance / stopLossDistance
        val suggestedLotSize = riskAmount / (stopLossDistance * pricePerLot)

        if (!riskReward.isFinite() || riskReward <= 0.0) return null
        if (!suggestedLotSize.isFinite() || suggestedLotSize <= 0.0) return null

        return RiskParameters(
            riskAmount = riskAmount,
            riskPercent = riskPercent,
            stopLossDistance = stopLossDistance,
            takeProfitDistance = takeProfitDistance,
            riskReward = riskReward,
            suggestedLotSize = suggestedLotSize
        )
    }
}
