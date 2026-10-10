package com.goldbee.analysis

import com.goldbee.decision.TradeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeProfitPlannerTest {

    @Test
    fun buyTargetsOfferSmallMediumAndLargeRiskMultiples() {
        val targets = TakeProfitPlanner.targets(
            direction = TradeDirection.BUY,
            entry = 4000.0,
            stopLoss = 3990.0
        )

        assertEquals(3, targets.size)
        assertEquals(4010.0, targets[0].price, 0.000001)
        assertEquals(4015.0, targets[1].price, 0.000001)
        assertEquals(4025.0, targets[2].price, 0.000001)
        assertEquals(100.0, targets[0].estimatedPips, 0.000001)
        assertEquals(150.0, targets[1].estimatedPips, 0.000001)
        assertEquals(250.0, targets[2].estimatedPips, 0.000001)
    }

    @Test
    fun sellTargetsAreBelowEntry() {
        val targets = TakeProfitPlanner.targets(
            direction = TradeDirection.SELL,
            entry = 4000.0,
            stopLoss = 4010.0,
            pipSize = 0.1
        )

        assertEquals(3990.0, targets[0].price, 0.000001)
        assertEquals(3985.0, targets[1].price, 0.000001)
        assertEquals(3975.0, targets[2].price, 0.000001)
        assertEquals(100.0, targets[0].estimatedPips, 0.000001)
        assertEquals(150.0, targets[1].estimatedPips, 0.000001)
        assertEquals(250.0, targets[2].estimatedPips, 0.000001)
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
