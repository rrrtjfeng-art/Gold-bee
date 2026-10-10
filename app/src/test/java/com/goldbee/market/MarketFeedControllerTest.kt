package com.goldbee.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFeedControllerTest {

    @Test
    fun seededHistoricalCandlesAreUpdatedByAcceptedLiveTick() {
        val controller = MarketFeedController()
        val timeframe = Timeframe.M15
        val nowMillis = System.currentTimeMillis()
        val nowSeconds = nowMillis / 1000L
        val candleStart = nowSeconds - (nowSeconds % timeframe.seconds)
        val historical = Candle(
            symbol = "XAU/USD",
            timeframe = timeframe,
            timestamp = candleStart,
            open = 3300.0,
            high = 3305.0,
            low = 3298.0,
            close = 3302.0,
            volume = 0.0
        )
        controller.seedHistoricalCandles(mapOf(timeframe to listOf(historical)))

        val tick = MarketTick(
            symbol = "XAUUSD",
            bid = 3306.0,
            ask = 3306.2,
            timestamp = System.currentTimeMillis(),
            source = "test-feed"
        )

        assertTrue(controller.submitTick(tick))
        val current = controller.getCandles(timeframe).last()
        assertEquals(3306.1, current.close, 0.000001)
        assertEquals(3306.1, current.high, 0.000001)
        assertEquals(3298.0, current.low, 0.000001)
    }
}
