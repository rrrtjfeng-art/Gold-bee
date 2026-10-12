package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketObservationEngineTest {

    @Test
    fun staleQuoteIsExplicitlyRejectedAsLiveObservation() {
        val now = 1_800_000_000_000L
        val result = MarketObservationEngine.observe(
            candlesByTimeframe = emptyMap(),
            currentPrice = 4_100.0,
            bid = 4_099.8,
            ask = 4_100.2,
            quoteTimestampMs = now - 20_000L,
            nowMs = now
        )

        assertFalse(result.quoteFresh)
        assertEquals("STALE_OR_MISSING_QUOTE", result.dataQuality)
        assertTrue(result.reasons.any { it.contains("过期") })
    }

    @Test
    fun reportsSpreadAndObservationsWhenQuoteIsFresh() {
        val now = 1_800_000_000_000L
        val candles = Timeframe.entries.associateWith { timeframe ->
            (0 until 60).map { index ->
                val base = 4_000.0 + index * 0.8
                Candle(
                    symbol = "XAUUSD",
                    timeframe = timeframe,
                    timestamp = index.toLong() * timeframe.seconds,
                    open = base,
                    high = base + 2.0,
                    low = base - 0.5,
                    close = base + 1.5
                )
            }
        }

        val result = MarketObservationEngine.observe(
            candlesByTimeframe = candles,
            currentPrice = 4_048.0,
            bid = 4_047.8,
            ask = 4_048.2,
            quoteTimestampMs = now - 500L,
            nowMs = now
        )

        assertTrue(result.quoteFresh)
        assertEquals(0.4, result.spread!!, 0.000001)
        assertNotNull(result.timeframes[Timeframe.M5])
        assertTrue(result.timeframes[Timeframe.H1]!!.candleCount == 60)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun insufficientHistoryNeverClaimsObservationReady() {
        val now = 1_800_000_000_000L
        val result = MarketObservationEngine.observe(
            candlesByTimeframe = mapOf(
                Timeframe.M5 to listOf(candle(Timeframe.M5, 1, 4_100.0)),
                Timeframe.M15 to listOf(candle(Timeframe.M15, 1, 4_100.0))
            ),
            currentPrice = 4_100.0,
            bid = 4_099.9,
            ask = 4_100.1,
            quoteTimestampMs = now,
            nowMs = now
        )

        assertEquals(ObservedRegime.INSUFFICIENT_DATA, result.regime)
        assertEquals("INSUFFICIENT_HISTORY", result.dataQuality)
        assertTrue(result.reasons.any { it.contains("历史不足") })
    }

    private fun candle(timeframe: Timeframe, index: Int, price: Double): Candle =
        Candle(
            symbol = "XAUUSD",
            timeframe = timeframe,
            timestamp = index.toLong() * timeframe.seconds,
            open = price,
            high = price + 1.0,
            low = price - 1.0,
            close = price + 0.2
        )
}
