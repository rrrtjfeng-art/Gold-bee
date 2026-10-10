package com.goldbee.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaperTradePerformanceTest {
    private fun record(id: String, exitAt: Long, pnl: Double) = PaperTradeHistoryRecord(
        id = id,
        direction = if (pnl >= 0.0) "BUY" else "SELL",
        source = "REAL",
        entryPrice = 4100.0,
        exitPrice = 4100.0 + pnl,
        stopLoss = 4090.0,
        takeProfit = 4110.0,
        entryTimestampMillis = exitAt - 1000,
        exitTimestampMillis = exitAt,
        exitReason = "TAKE_PROFIT",
        lotSize = 0.01,
        contractSizeOunces = 100.0,
        commissionPerLotRoundTurnUsd = 0.0,
        entrySpread = 0.2,
        exitSpread = 0.2,
        pnlPrice = pnl,
        pnlUsd = pnl
    )

    @Test
    fun calculatesNetStatsProfitFactorAndDrawdown() {
        val summary = PaperTradePerformance.summarize(
            listOf(
                record("a", 1000L, 50.0),
                record("b", 2000L, -20.0),
                record("c", 3000L, 10.0)
            ),
            initialBalanceUsd = 1000.0
        )
        assertEquals(3, summary.trades)
        assertEquals(2, summary.wins)
        assertEquals(1, summary.losses)
        assertEquals(0, summary.flats)
        assertEquals(66.666666, summary.winRatePercent, 0.001)
        assertEquals(40.0, summary.totalNetUsd, 0.000001)
        assertEquals(60.0, summary.grossProfitUsd, 0.000001)
        assertEquals(20.0, summary.grossLossUsd, 0.000001)
        assertEquals(3.0, summary.profitFactor!!, 0.000001)
        assertEquals(20.0, summary.maxDrawdownUsd, 0.000001)
        assertEquals(1040.0, summary.endingBalanceUsd, 0.000001)
    }

    @Test
    fun noLossesDoesNotPretendProfitFactorIsFinite() {
        val summary = PaperTradePerformance.summarize(
            listOf(record("a", 1000L, 5.0)),
            initialBalanceUsd = 1000.0
        )
        assertNull(summary.profitFactor)
        assertNotNull(summary)
    }
}
