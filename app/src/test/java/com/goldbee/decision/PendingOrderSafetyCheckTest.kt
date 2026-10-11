package com.goldbee.decision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOrderSafetyCheckTest {
    private fun buyLimit() = PendingOrderPlan(
        type = PendingOrderType.BUY_LIMIT,
        entry = 4098.0,
        stopLoss = 4090.0,
        takeProfit1 = 4110.0,
        takeProfit2 = 4118.0,
        riskReward1 = 1.5,
        riskReward2 = 2.5,
        expiresAfterM5Bars = 3,
        cancelIfPriceBeyond = 4101.0,
        rationale = "test"
    )

    @Test
    fun acceptsFreshQuoteAndCorrectPendingSide() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4100.0, ask = 4100.2,
            atrM5 = 4.0, quoteAgeMillis = 500L, brokerMinimumDistance = 0.5
        )
        assertTrue(result.allowed)
    }

    @Test
    fun rejectsStaleQuote() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4100.0, ask = 4100.2,
            atrM5 = 4.0, quoteAgeMillis = 3_001L
        )
        assertFalse(result.allowed)
    }

    @Test
    fun rejectsEntryOnWrongSideOfExecutableQuote() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4097.0, ask = 4097.2,
            atrM5 = 4.0, quoteAgeMillis = 500L
        )
        assertFalse(result.allowed)
    }

    @Test
    fun rejectsExcessiveSpreadRelativeToAtr() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4099.0, ask = 4100.0,
            atrM5 = 4.0, quoteAgeMillis = 500L
        )
        assertFalse(result.allowed)
    }

    @Test
    fun rejectsSetupAfterPriceCrossesItsInvalidationBoundary() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4101.0, ask = 4101.2,
            atrM5 = 4.0, quoteAgeMillis = 500L
        )
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("失效边界"))
    }

    @Test
    fun acceptsSetupWhilePriceRemainsBeforeInvalidationBoundary() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4100.0, ask = 4100.2,
            atrM5 = 4.0, quoteAgeMillis = 500L
        )
        assertTrue(result.allowed)
    }

    @Test
    fun rejectsEntryInsideBrokerMinimumDistance() {
        val result = PendingOrderSafetyCheck.evaluate(
            plan = buyLimit(), bid = 4100.0, ask = 4100.2,
            atrM5 = 4.0, quoteAgeMillis = 500L, brokerMinimumDistance = 3.0
        )
        assertFalse(result.allowed)
    }
}
