package com.goldbee.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOrderPlannerTest {
    @Test
    fun bullishPullbackProducesBuyLimitBelowMarket() {
        val plan = PendingOrderPlanner.plan(
            direction = TradeDirection.BUY,
            currentPrice = 4100.0,
            atrM5 = 4.0,
            structuralStop = 4090.0,
            structuralTarget = 4125.0,
            preferBreakout = false
        )
        assertNotNull(plan)
        assertEquals(PendingOrderType.BUY_LIMIT, plan!!.type)
        assertTrue(plan.entry < 4100.0)
        assertTrue(plan.stopLoss < plan.entry)
        assertTrue(plan.takeProfit1 > plan.entry)
        assertTrue(plan.takeProfit2 >= plan.takeProfit1)
        assertTrue(plan.isValid())
    }

    @Test
    fun bearishBreakdownProducesSellStopBelowMarket() {
        val plan = PendingOrderPlanner.plan(
            direction = TradeDirection.SELL,
            currentPrice = 4100.0,
            atrM5 = 4.0,
            structuralStop = 4110.0,
            structuralTarget = 4075.0,
            preferBreakout = true
        )
        assertNotNull(plan)
        assertEquals(PendingOrderType.SELL_STOP, plan!!.type)
        assertTrue(plan.entry < 4100.0)
        assertTrue(plan.stopLoss > plan.entry)
        assertTrue(plan.takeProfit1 < plan.entry)
        assertTrue(plan.isValid())
    }

    @Test
    fun insufficientStructuralRewardRejectsPlan() {
        assertNull(
            PendingOrderPlanner.plan(
                direction = TradeDirection.BUY,
                currentPrice = 4100.0,
                atrM5 = 4.0,
                structuralStop = 4090.0,
                structuralTarget = 4105.0,
                preferBreakout = false
            )
        )
    }

    @Test
    fun invalidMarketInputsRejectPlan() {
        assertNull(
            PendingOrderPlanner.plan(
                direction = TradeDirection.BUY,
                currentPrice = Double.NaN,
                atrM5 = 4.0,
                structuralStop = 4090.0,
                structuralTarget = 4125.0,
                preferBreakout = false
            )
        )
    }
}
