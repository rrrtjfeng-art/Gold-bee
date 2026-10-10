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
    fun calculatesRiskSizingForValidInputs() {
        val result = RiskManager.calculate(
            balance = 1000.0,
            riskPercent = 1.0,
            setup = validBuySetup,
            pricePerLot = 100.0
        )
        assertEquals(10.0, result!!.riskAmount, 0.000001)
        assertEquals(0.1, result.suggestedLotSize, 0.000001)
        assertEquals(2.0, result.riskReward, 0.000001)
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeInputs() {
        assertNull(RiskManager.calculate(Double.NaN, 1.0, validBuySetup))
        assertNull(RiskManager.calculate(1000.0, Double.POSITIVE_INFINITY, validBuySetup))
        assertNull(RiskManager.calculate(1000.0, 101.0, validBuySetup))
        assertNull(RiskManager.calculate(1000.0, 1.0, validBuySetup, Double.NaN))
    }
}
