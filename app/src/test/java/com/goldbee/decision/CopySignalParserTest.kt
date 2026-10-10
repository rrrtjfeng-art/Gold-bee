package com.goldbee.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySignalParserTest {

    @Test
    fun parsesExplicitBuyEntryStopAndTarget() {
        val result = CopySignalParser.parse("BUY XAUUSD ENTRY: 4110.50 SL: 4105.00 TP: 4120.00")

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(TradeDirection.BUY, signal.direction)
        assertEquals(4110.50, signal.entry ?: Double.NaN, 0.000001)
        assertEquals(4105.00, signal.stopLoss ?: Double.NaN, 0.000001)
        assertEquals(4120.00, signal.takeProfit ?: Double.NaN, 0.000001)
    }

    @Test
    fun parsesExplicitSellSignal() {
        val result = CopySignalParser.parse("SELL GOLD ENTRY 4110 SL 4118 TP 4095")

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(TradeDirection.SELL, signal.direction)
        assertEquals(4110.0, signal.entry ?: Double.NaN, 0.000001)
        assertEquals(4118.0, signal.stopLoss ?: Double.NaN, 0.000001)
        assertEquals(4095.0, signal.takeProfit ?: Double.NaN, 0.000001)
    }

    @Test
    fun rejectsSignalsWithBothDirections() {
        val result = CopySignalParser.parse("BUY XAUUSD ENTRY 4110 SL 4100 TP 4130; SELL XAUUSD")

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsBlankInput() {
        val result = CopySignalParser.parse("   ")

        assertTrue(result.isFailure)
    }

    @Test
    fun doesNotInventMissingEntryStopOrTarget() {
        val result = CopySignalParser.parse("BUY XAUUSD")

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertNull(signal.entry)
        assertNull(signal.stopLoss)
        assertNull(signal.takeProfit)
        assertFalse(signal.hasExplicitEntry())
        assertFalse(signal.hasStopLoss())
        assertFalse(signal.hasTakeProfit())
    }

    @Test
    fun parsesChineseDirectionAndPriceLabels() {
        val result = CopySignalParser.parse("做多 入场 4110 止损 4100 止盈 4125")

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(TradeDirection.BUY, signal.direction)
        assertEquals(4110.0, signal.entry ?: Double.NaN, 0.000001)
        assertEquals(4100.0, signal.stopLoss ?: Double.NaN, 0.000001)
        assertEquals(4125.0, signal.takeProfit ?: Double.NaN, 0.000001)
    }
}
