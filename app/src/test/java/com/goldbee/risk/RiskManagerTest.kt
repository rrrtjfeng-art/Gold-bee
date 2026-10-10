package com.goldbee.risk

import com.goldbee.decision.TradeDirection
import com.goldbee.decision.TradeSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiskManagerTest {

    private val validBuySetup = TradeSetup(
        direction = TradeDirection.BUY,
        entry = 100.0,
        stopLoss = 99.0,
        takeProfit = 102.0,
        riskReward = 2.0,
        reason = "Unit test setup"
    )

    @Test
    fun calculatesRiskSizingOnlyWithExplicitContractValue() {
        val result = RiskManager.calculate(
            balance = 1000.0,
            riskPercent = 1.0,
            setup = validBuySetup,
            accountCurrencyValuePerPriceUnitPerLot = 100.0
        )
        assertEquals(10.0, result!!.riskAmount, 0.000001)
        assertEquals(0.1, result.suggestedLotSize, 0.000001)
        assertEquals(2.0, result.riskReward, 0.000001)
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeInputs() {
        assertNull(
            RiskManager.calculate(
                Double.NaN, 1.0, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = 100.0
            )
        )
        assertNull(
            RiskManager.calculate(
                1000.0, Double.POSITIVE_INFINITY, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = 100.0
            )
        )
        assertNull(
            RiskManager.calculate(
                1000.0, 101.0, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = 100.0
            )
        )
        assertNull(
            RiskManager.calculate(
                1000.0, 1.0, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = Double.NaN
            )
        )
        assertNull(
            RiskManager.calculate(
                1000.0, 1.0, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = 0.0
            )
        )
        assertNull(
            RiskManager.calculate(
                1000.0, 1.0, validBuySetup,
                accountCurrencyValuePerPriceUnitPerLot = Double.POSITIVE_INFINITY
            )
        )
    }

    @Test
    fun rejectsOverflowInLossPerLotCalculation() {
        val largeStopSetup = validBuySetup.copy(
            entry = 1.0e200,
            stopLoss = 1.0,
            takeProfit = 1.0e200 + 1.0e190,
            riskReward = 1.0e-10
        )
        assertNull(
            RiskManager.calculate(
                balance = 1000.0,
                riskPercent = 1.0,
                setup = largeStopSetup,
                accountCurrencyValuePerPriceUnitPerLot = 1.0e200
            )
        )
    }
}
