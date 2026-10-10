package com.goldbee.mt5

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mt5QuoteFreshnessTest {
    @Test fun changedPriceRefreshesAge() {
        assertTrue(Mt5QuoteFreshness.isNewExecutableQuote("XAUUSD", 4100.1, 4100.3, 4100.0, 4100.2))
    }

    @Test fun unchangedPriceDoesNotRefreshAge() {
        assertFalse(Mt5QuoteFreshness.isNewExecutableQuote("XAUUSD", 4100.0, 4100.2, 4100.0, 4100.2))
    }

    @Test fun invalidQuoteDoesNotRefreshAge() {
        assertFalse(Mt5QuoteFreshness.isNewExecutableQuote("EURUSD", 1.1, 1.2, null, null))
        assertFalse(Mt5QuoteFreshness.isNewExecutableQuote("XAUUSD", 4100.0, 4100.0, null, null))
    }

    @Test fun firstValidQuoteCanRefreshAge() {
        assertTrue(Mt5QuoteFreshness.isNewExecutableQuote("GOLD", 4100.0, 4100.2, null, null))
    }
}
