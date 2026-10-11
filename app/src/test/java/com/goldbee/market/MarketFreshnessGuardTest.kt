package com.goldbee.market

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFreshnessGuardTest {

    private val guard = MarketFreshnessGuard(maxAgeMillis = 3_000L)

    private fun snapshot(
        bid: Double = 4_100.0,
        ask: Double = 4_100.2,
        receivedAt: Long = System.currentTimeMillis()
    ) = MarketSnapshot(
        symbol = "XAUUSD",
        bid = bid,
        ask = ask,
        timestamp = receivedAt,
        candles = emptyMap(),
        source = "test",
        receivedAt = receivedAt
    )

    @Test
    fun acceptsFreshValidQuote() {
        assertTrue(guard.isFresh(snapshot()))
    }

    @Test
    fun rejectsQuoteOlderThanMaximumAge() {
        assertFalse(guard.isFresh(snapshot(receivedAt = System.currentTimeMillis() - 3_500L)))
    }

    @Test
    fun rejectsFutureReceivedTimestamp() {
        assertFalse(guard.isFresh(snapshot(receivedAt = System.currentTimeMillis() + 5_000L)))
    }

    @Test
    fun rejectsNonPositiveBid() {
        assertFalse(guard.isFresh(snapshot(bid = 0.0, ask = 0.2)))
    }

    @Test
    fun rejectsAskBelowBid() {
        assertFalse(guard.isFresh(snapshot(bid = 4_100.2, ask = 4_100.0)))
    }
}
