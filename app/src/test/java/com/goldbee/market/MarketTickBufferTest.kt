package com.goldbee.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTickBufferTest {

    private fun tick(
        timestamp: Long,
        bid: Double = 2350.0,
        ask: Double = 2350.3,
        symbol: String = "XAUUSD"
    ) = MarketTick(symbol, bid, ask, timestamp, "test")

    @Test
    fun addAcceptsFreshTicksAndKeepsChronologicalOrder() {
        val buffer = MarketTickBuffer(maxTicks = 3, maxAgeMillis = 10_000L)

        assertTrue(buffer.add(tick(9_000L), nowMillis = 10_000L))
        assertTrue(buffer.add(tick(9_100L, bid = 2350.1, ask = 2350.4), nowMillis = 10_000L))
        assertEquals(2, buffer.size())
        assertEquals(9_100L, buffer.latest()?.timestamp)
    }

    @Test
    fun addRejectsStaleFutureInvalidAndWrongSymbolTicks() {
        val buffer = MarketTickBuffer(maxAgeMillis = 3_000L)
        assertFalse(buffer.add(tick(6_999L), nowMillis = 10_000L))
        assertFalse(buffer.add(tick(10_001L), nowMillis = 10_000L))
        assertFalse(buffer.add(tick(9_000L, bid = Double.NaN), nowMillis = 10_000L))
        assertTrue(buffer.add(tick(9_000L), nowMillis = 10_000L))
        assertFalse(buffer.add(tick(9_100L, symbol = "EURUSD"), nowMillis = 10_000L))
        assertEquals(1, buffer.size())
    }

    @Test
    fun addRejectsOutOfOrderAndExactDuplicateTicks() {
        val buffer = MarketTickBuffer(maxAgeMillis = 10_000L)
        assertTrue(buffer.add(tick(9_000L), nowMillis = 10_000L))
        assertFalse(buffer.add(tick(9_000L), nowMillis = 10_000L))
        assertFalse(buffer.add(tick(8_999L, bid = 2350.1, ask = 2350.4), nowMillis = 10_000L))
        assertEquals(1, buffer.size())
    }

    @Test
    fun bufferTrimsOldestTicksWhenCapacityIsReached() {
        val buffer = MarketTickBuffer(maxTicks = 2, maxAgeMillis = 10_000L)
        assertTrue(buffer.add(tick(9_000L), nowMillis = 10_000L))
        assertTrue(buffer.add(tick(9_100L, bid = 2350.1, ask = 2350.4), nowMillis = 10_000L))
        assertTrue(buffer.add(tick(9_200L, bid = 2350.2, ask = 2350.5), nowMillis = 10_000L))

        assertEquals(2, buffer.size())
        assertEquals(9_100L, buffer.recent(2).first().timestamp)
        assertEquals(9_200L, buffer.latest()?.timestamp)
    }

    @Test
    fun removeExpiredClearsStaleTicksAndClearResetsBuffer() {
        val buffer = MarketTickBuffer(maxAgeMillis = 3_000L)
        assertTrue(buffer.add(tick(9_000L), nowMillis = 10_000L))
        buffer.removeExpired(nowMillis = 12_001L)
        assertEquals(0, buffer.size())
        assertNull(buffer.latest())

        assertTrue(buffer.add(tick(12_000L), nowMillis = 12_000L))
        buffer.clear()
        assertEquals(0, buffer.size())
    }
}
