package com.goldbee.market

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTickTest {

    private fun tick(
        bid: Double = 2350.0,
        ask: Double = 2350.3,
        timestamp: Long = 10_000L,
        symbol: String = "XAUUSD",
        source: String = "test"
    ) = MarketTick(symbol, bid, ask, timestamp, source)

    @Test
    fun validTickAcceptsFinitePositivePricesAndNonNegativeSpread() {
        assertTrue(tick().isValid())
        assertTrue(tick(bid = 2350.0, ask = 2350.0).isValid())
    }

    @Test
    fun invalidTickRejectsNonFiniteAndNonPositivePrices() {
        assertFalse(tick(bid = Double.NaN).isValid())
        assertFalse(tick(ask = Double.NaN).isValid())
        assertFalse(tick(bid = Double.POSITIVE_INFINITY).isValid())
        assertFalse(tick(ask = Double.NEGATIVE_INFINITY).isValid())
        assertFalse(tick(bid = 0.0).isValid())
        assertFalse(tick(ask = -1.0).isValid())
        assertFalse(tick(bid = 2351.0, ask = 2350.0).isValid())
    }

    @Test
    fun invalidTickRejectsMissingIdentityAndTimestamp() {
        assertFalse(tick(symbol = " ").isValid())
        assertFalse(tick(source = " ").isValid())
        assertFalse(tick(timestamp = 0L).isValid())
        assertFalse(tick(timestamp = -1L).isValid())
    }

    @Test
    fun freshnessAcceptsExactAgeBoundary() {
        assertTrue(tick(timestamp = 7_000L).isFresh(nowMillis = 10_000L, maxAgeMillis = 3_000L))
    }

    @Test
    fun freshnessRejectsStaleFutureAndInvalidLimits() {
        assertFalse(tick(timestamp = 6_999L).isFresh(nowMillis = 10_000L, maxAgeMillis = 3_000L))
        assertFalse(tick(timestamp = 10_001L).isFresh(nowMillis = 10_000L, maxAgeMillis = 3_000L))
        assertFalse(tick().isFresh(nowMillis = 10_000L, maxAgeMillis = -1L))
        assertFalse(tick(timestamp = Long.MAX_VALUE).isFresh(nowMillis = 10_000L))
    }

    @Test
    fun symbolComparisonIsCaseInsensitiveButRejectsBlankExpectedSymbol() {
        assertTrue(tick(symbol = "xauusd").isSymbol("XAUUSD"))
        assertFalse(tick().isSymbol(" "))
        assertFalse(tick(symbol = "EURUSD").isSymbol("XAUUSD"))
    }
}
