package com.goldbee.analysis

import com.goldbee.decision.TradeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeProfitPlannerTest {

    @Test
    fun buyTargetsUseSmallMediumAndLargeAbsolutePriceDistances() {
        val targets = TakeProfitPlanner.targets(
            direction = TradeDirection.BUY,
            entry = 4110.0,
            stopLoss = 4106.0
        )

        assertEquals(3, targets.size)
        assertEquals(4112.0, targets[0].price, 0.000001)
        assertEquals(4115.0, targets[1].price, 0.000001)
        assertEquals(4120.0, targets[2].price, 0.000001)
        assertEquals(20.0, targets[0].estimatedPips, 0.000001)
        assertEquals(50.0, targets[1].estimatedPips, 0.000001)
        assertEquals(100.0, targets[2].estimatedPips, 0.000001)
        assertEquals(0.5, targets[0].riskReward, 0.000001)
        assertEquals(1.25, targets[1].riskReward, 0.000001)
        assertEquals(2.5, targets[2].riskReward, 0.000001)
    }

    @Test
    fun sellTargetsAreBelowEntryAndPipsUseConfiguredConvention() {
        val targets = TakeProfitPlanner.targets(
            direction = TradeDirection.SELL,
            entry = 4110.0,
            stopLoss = 4114.0,
            pipSize = 0.01
        )

        assertEquals(4108.0, targets[0].price, 0.000001)
        assertEquals(4105.0, targets[1].price, 0.000001)
        assertEquals(4100.0, targets[2].price, 0.000001)
        assertEquals(200.0, targets[0].estimatedPips, 0.000001)
        assertEquals(500.0, targets[1].estimatedPips, 0.000001)
        assertEquals(1000.0, targets[2].estimatedPips, 0.000001)
    }

    @Test
    fun rejectsInvalidPipSizeAndWrongSideStop() {
        val invalidPipRejected = runCatching {
            TakeProfitPlanner.targets(TradeDirection.BUY, 4000.0, 3990.0, 0.0)
        }.isFailure
        val wrongSideRejected = runCatching {
            TakeProfitPlanner.targets(TradeDirection.BUY, 4000.0, 4010.0)
        }.isFailure

        assertTrue(invalidPipRejected)
        assertTrue(wrongSideRejected)
    }
}
