package com.goldbee.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GoldPriceDevTickParserTest {

    @Test
    fun parsesDocumentedGoldSpotTick() {
        val timestamp = "2026-10-10T07:10:00Z"
        val tick = GoldPriceDevTickParser.parse(
            """{"type":"tick","symbol":"XAU-USD-SPOT","price":"3312.45","bid":"3312.40","ask":"3312.50","computed_at":"$timestamp"}"""
        )

        requireNotNull(tick)
        assertEquals("XAUUSD", tick.symbol)
        assertEquals(3312.40, tick.bid, 0.000001)
        assertEquals(3312.50, tick.ask, 0.000001)
        assertEquals(Instant.parse(timestamp).toEpochMilli(), tick.timestamp)
        assertEquals(GoldPriceDevTickParser.SOURCE, tick.source)
    }

    @Test
    fun rejectsTickWhenBidOrAskIsMissingRatherThanInventingPrices() {
        assertNull(
            GoldPriceDevTickParser.parse(
                """{"type":"tick","symbol":"XAU-USD-SPOT","price":"3312.45","computed_at":"2026-10-10T07:10:00Z"}"""
            )
        )
    }

    @Test
    fun rejectsWrongSymbol() {
        assertNull(
            GoldPriceDevTickParser.parse(
                """{"type":"tick","symbol":"XAG-USD-SPOT","bid":"32.17","ask":"32.19","computed_at":"2026-10-10T07:10:00Z"}"""
            )
        )
    }

    @Test
    fun rejectsInvalidSpreadAndTimestamp() {
        assertNull(
            GoldPriceDevTickParser.parse(
                """{"type":"tick","symbol":"XAU-USD-SPOT","bid":"3313.00","ask":"3312.00","computed_at":"2026-10-10T07:10:00Z"}"""
            )
        )
        assertNull(
            GoldPriceDevTickParser.parse(
                """{"type":"tick","symbol":"XAU-USD-SPOT","bid":"3312.40","ask":"3312.50","computed_at":"not-a-time"}"""
            )
        )
    }

    @Test
    fun rejectsNonTickFrames() {
        assertNull(
            GoldPriceDevTickParser.parse(
                """{"type":"heartbeat"}"""
            )
        )
    }
}
