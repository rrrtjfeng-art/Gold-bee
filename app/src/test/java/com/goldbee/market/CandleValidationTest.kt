package com.goldbee.market

import org.junit.Test

class CandleValidationTest {

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInfiniteOhlcPrices() {
        Candle(
            symbol = "XAUUSD",
            timeframe = Timeframe.M5,
            timestamp = 1_800_000_000L,
            open = Double.POSITIVE_INFINITY,
            high = Double.POSITIVE_INFINITY,
            low = 100.0,
            close = 101.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNanOhlcPrices() {
        Candle(
            symbol = "XAUUSD",
            timeframe = Timeframe.M5,
            timestamp = 1_800_000_000L,
            open = 100.0,
            high = 102.0,
            low = 99.0,
            close = Double.NaN
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInfiniteVolume() {
        Candle(
            symbol = "XAUUSD",
            timeframe = Timeframe.M5,
            timestamp = 1_800_000_000L,
            open = 100.0,
            high = 102.0,
            low = 99.0,
            close = 101.0,
            volume = Double.POSITIVE_INFINITY
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeVolume() {
        Candle(
            symbol = "XAUUSD",
            timeframe = Timeframe.M5,
            timestamp = 1_800_000_000L,
            open = 100.0,
            high = 102.0,
            low = 99.0,
            close = 101.0,
            volume = -1.0
        )
    }
}
