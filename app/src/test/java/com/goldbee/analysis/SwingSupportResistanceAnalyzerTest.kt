package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingSupportResistanceAnalyzerTest {

    @Test
    fun confirmsSwingHighAndLowOnlyAfterRightBarsExist() {
        val candles = listOf(
            candle(0, 10.0, 8.0),
            candle(1, 11.0, 9.0),
            candle(2, 15.0, 7.0),
            candle(3, 12.0, 8.0),
            candle(4, 11.0, 9.0),
            candle(5, 10.0, 6.0),
            candle(6, 12.0, 8.0),
            candle(7, 13.0, 9.0)
        )

        val result = SwingSupportResistanceAnalyzer.analyze(
            candles = candles,
            leftBars = 2,
            rightBars = 2,
            currentPrice = 10.0
        )

        assertTrue(result.swingHighs.any { it.index == 2 && it.price == 15.0 && it.confirmedAtIndex == 4 })
        assertTrue(result.swingLows.any { it.index == 5 && it.price == 6.0 && it.confirmedAtIndex == 7 })
    }

    @Test
    fun clustersNearbySwingLowsAndRanksNearestSupportBelowPrice() {
        val candles = listOf(
            candle(0, 12.0, 10.0),
            candle(1, 13.0, 11.0),
            candle(2, 14.0, 9.0),
            candle(3, 12.0, 10.0),
            candle(4, 13.0, 11.0),
            candle(5, 12.0, 9.2),
            candle(6, 13.0, 10.0),
            candle(7, 15.0, 12.0),
            candle(8, 16.0, 13.0)
        )

        val result = SwingSupportResistanceAnalyzer.analyze(
            candles = candles,
            leftBars = 1,
            rightBars = 1,
            currentPrice = 14.0,
            atr = 1.0
        )

        assertNotNull(result.nearestSupport)
        assertTrue(result.nearestSupport!!.midpoint < 14.0)
        assertTrue(result.supportZones.any { it.touches >= 2 })
    }

    @Test
    fun reportsNoPivotsWhenThereAreNotEnoughCandles() {
        val result = SwingSupportResistanceAnalyzer.analyze(
            candles = listOf(candle(0, 10.0, 8.0), candle(1, 11.0, 9.0)),
            leftBars = 2,
            rightBars = 2
        )

        assertTrue(result.swingHighs.isEmpty())
        assertTrue(result.swingLows.isEmpty())
        assertNull(result.nearestSupport)
        assertNull(result.nearestResistance)
    }

    @Test
    fun rejectsInvalidPivotSettings() {
        val candles = listOf(candle(0, 10.0, 8.0))
        try {
            SwingSupportResistanceAnalyzer.analyze(candles, leftBars = 0)
            throw AssertionError("Expected invalid leftBars to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("leftBars must be at least 1", expected.message)
        }
    }

    private fun candle(index: Int, high: Double, low: Double): Candle {
        val open = (high + low) / 2.0
        return Candle(
            symbol = "XAUUSD",
            timeframe = Timeframe.M15,
            timestamp = index * Timeframe.M15.seconds * 1000L,
            open = open,
            high = high,
            low = low,
            close = open
        )
    }
}
