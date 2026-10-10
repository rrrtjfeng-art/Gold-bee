package com.goldbee.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceQuoteTimestampTest {

    @Test
    fun parsesExplicitUtcAndOffsetTimestamps() {
        assertEquals(1_767_225_600_000L, SourceQuoteTimestamp.parseMillis("2026-01-01T00:00:00Z"))
        assertEquals(1_767_225_600_000L, SourceQuoteTimestamp.parseMillis("2026-01-01T08:00:00+08:00"))
    }

    @Test
    fun parsesEpochSecondsAndMilliseconds() {
        assertEquals(1_767_225_600_000L, SourceQuoteTimestamp.parseMillis("1767225600"))
        assertEquals(1_767_225_600_000L, SourceQuoteTimestamp.parseMillis("1767225600000"))
    }

    @Test
    fun refusesTimestampsWithoutExplicitTimezoneOrInvalidValues() {
        assertNull(SourceQuoteTimestamp.parseMillis("2026-01-01 08:00:00"))
        assertNull(SourceQuoteTimestamp.parseMillis("not-a-timestamp"))
        assertNull(SourceQuoteTimestamp.parseMillis("123"))
    }

    @Test
    fun freshnessUsesSourceTimestampNotReceiveTime() {
        val now = 1_767_225_600_000L
        assertTrue(SourceQuoteTimestamp.isFresh(now - 90_000L, now))
        assertFalse(SourceQuoteTimestamp.isFresh(now - 90_001L, now))
        assertFalse(SourceQuoteTimestamp.isFresh(now + 1L, now))
        assertFalse(SourceQuoteTimestamp.isFresh(null, now))
    }
}
