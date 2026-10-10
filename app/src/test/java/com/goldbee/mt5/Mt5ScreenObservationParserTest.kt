package com.goldbee.mt5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mt5ScreenObservationParserTest {
    @Test
    fun extractsExplicitSignalLevelsFromVisibleLabels() {
        val result = Mt5ScreenObservationParser.parse(
            listOf("XAUUSD", "M15", "SELL", "Entry: 4320.5", "SL: 4330.0", "TP1: 4300.0", "Bid: 4318.2", "Ask: 4318.5")
        )

        assertEquals("XAUUSD", result.symbol)
        assertEquals("M15", result.timeframe)
        assertEquals("SELL", result.direction)
        assertEquals(4320.5, result.entry!!, 0.0001)
        assertEquals(4330.0, result.stopLoss!!, 0.0001)
        assertEquals(4300.0, result.takeProfit!!, 0.0001)
        assertEquals(4318.2, result.bid!!, 0.0001)
        assertEquals(4318.5, result.ask!!, 0.0001)
        assertTrue(result.hasTradeLevels)
    }

    @Test
    fun doesNotInventMissingEntryOrStopLoss() {
        val result = Mt5ScreenObservationParser.parse(listOf("GOLD", "BUY", "TP: 4400"))
        assertEquals("GOLD", result.symbol)
        assertEquals("BUY", result.direction)
        assertFalse(result.hasTradeLevels)
        assertEquals(null, result.entry)
        assertEquals(null, result.stopLoss)
    }

    @Test
    fun ignoresUnrelatedTextAndInvalidPrices() {
        val result = Mt5ScreenObservationParser.parse(listOf("Account", "Balance", "BUY", "Entry: 0", "SL: 99999999", "TP: 4350"))
        assertEquals(null, result.symbol)
        assertEquals(null, result.entry)
        assertEquals(null, result.stopLoss)
        assertEquals(4350.0, result.takeProfit!!, 0.0001)
    }
}
