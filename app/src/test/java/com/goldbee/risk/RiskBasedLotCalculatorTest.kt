package com.goldbee.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskBasedLotCalculatorTest {
    @Test
    fun rejectsMinimumLotWhenItWouldExceedRiskBudget() {
        val estimate = RiskBasedLotCalculator.calculate(
            balanceUsd = 10.0,
            riskPercent = 0.5,
            entry = 4100.0,
            stopLoss = 4095.0,
            contractSizeOuncesPerLot = 100.0,
            minimumLot = 0.01,
            lotStep = 0.01
        )
        assertNotNull(estimate)
        assertFalse(estimate!!.isTradable)
        assertEquals(0.0, estimate.lots, 1e-9)
        assertEquals(0.05, estimate.riskBudgetUsd, 1e-9)
        assertEquals(5.0, estimate.minimumLotLossUsd, 1e-9)
    }

    @Test
    fun roundsLotsDownSoEstimatedLossDoesNotExceedBudget() {
        val estimate = RiskBasedLotCalculator.calculate(
            balanceUsd = 10000.0,
            riskPercent = 1.0,
            entry = 4100.0,
            stopLoss = 4090.0,
            contractSizeOuncesPerLot = 100.0,
            roundTurnCommissionPerLotUsd = 2.0,
            minimumLot = 0.01,
            lotStep = 0.01
        )
        assertNotNull(estimate)
        assertTrue(estimate!!.isTradable)
        assertEquals(0.09, estimate.lots, 1e-9)
        assertTrue(estimate.estimatedLossUsd <= estimate.riskBudgetUsd)
    }

    @Test
    fun rejectsInvalidRiskInputs() {
        assertNull(
            RiskBasedLotCalculator.calculate(
                balanceUsd = 10.0,
                riskPercent = 10.0,
                entry = 4100.0,
                stopLoss = 4090.0,
                contractSizeOuncesPerLot = 100.0
            )
        )
    }
}
