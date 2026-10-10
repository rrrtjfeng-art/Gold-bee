package com.goldbee.paper

import com.goldbee.decision.TradeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTradingEngineTest {
    private val now = 1_800_000_000_000L
    private fun quote(bid: Double, ask: Double, at: Long = now) =
        PaperQuote("XAUUSD", bid, ask, at)

    @Test
    fun buyUsesAskForEntryAndBidForExit() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val opened = PaperTradingEngine.open(signal, quote(4099.8, 4100.2), now, 1.0)
        assertTrue(opened.accepted)
        assertEquals(4100.2, opened.trade!!.entryPrice, 0.000001)

        val closed = PaperTradingEngine.update(
            opened.trade,
            quote(4110.1, 4110.3, now + 1000),
            now + 1000
        )
        assertTrue(closed.accepted)
        assertEquals(PaperTradeStatus.CLOSED, closed.trade!!.status)
        assertEquals(4110.0, closed.trade.exitPrice!!, 0.000001)
        assertEquals(9.8, closed.trade.pnlPrice!!, 0.000001)
    }

    @Test
    fun sellUsesBidForEntryAndAskForExit() {
        val signal = PaperSignal(TradeDirection.SELL, 4100.0, 4105.0, 4090.0, "TEST", now)
        val opened = PaperTradingEngine.open(signal, quote(4099.8, 4100.2), now, 1.0)
        assertTrue(opened.accepted)
        assertEquals(4099.8, opened.trade!!.entryPrice, 0.000001)

        val closed = PaperTradingEngine.update(
            opened.trade,
            quote(4089.6, 4089.8, now + 1000),
            now + 1000
        )
        assertTrue(closed.accepted)
        assertEquals(4090.0, closed.trade!!.exitPrice!!, 0.000001)
        assertEquals(9.8, closed.trade.pnlPrice!!, 0.000001)
    }

    @Test
    fun staleQuoteCannotOpenTrade() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val result = PaperTradingEngine.open(signal, quote(4099.8, 4100.2, now - 3001), now, 1.0)
        assertFalse(result.accepted)
        assertNull(result.trade)
    }

    @Test
    fun movedPriceCannotBeChased() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val result = PaperTradingEngine.open(signal, quote(4102.0, 4102.2), now, 1.0)
        assertFalse(result.accepted)
    }

    @Test
    fun stopGapUsesWorseCurrentExitQuote() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val opened = PaperTradingEngine.open(signal, quote(4099.8, 4100.2), now, 1.0)
        assertNotNull(opened.trade)
        val closed = PaperTradingEngine.update(opened.trade!!, quote(4093.0, 4093.2, now + 1000), now + 1000)
        assertEquals(PaperExitReason.STOP_LOSS, closed.trade!!.exitReason)
        assertEquals(4093.0, closed.trade.exitPrice!!, 0.000001)
    }

    @Test
    fun manualCloseUsesCorrectClosingSide() {
        val signal = PaperSignal(TradeDirection.SELL, 4100.0, 4105.0, 4090.0, "TEST", now)
        val opened = PaperTradingEngine.open(signal, quote(4099.8, 4100.2), now, 1.0)
        val closed = PaperTradingEngine.closeManually(opened.trade!!, quote(4098.0, 4098.3, now + 1000), now + 1000)
        assertEquals(PaperExitReason.MANUAL, closed.trade!!.exitReason)
        assertEquals(4098.3, closed.trade.exitPrice!!, 0.000001)
        assertEquals(1.5, closed.trade.pnlPrice!!, 0.000001)
    }
    @Test
    fun netUsdPnlUsesLotContractAndRoundTripCommission() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val opened = PaperTradingEngine.open(
            signal = signal,
            quote = quote(4099.8, 4100.2),
            nowMillis = now,
            maxEntryDistance = 1.0,
            lotSize = 0.10,
            contractSizeOunces = 100.0,
            commissionPerLotRoundTurnUsd = 7.0
        )
        assertTrue(opened.accepted)
        val closed = PaperTradingEngine.update(
            opened.trade!!,
            quote(4110.1, 4110.3, now + 1000),
            now + 1000
        )
        assertEquals(9.8, closed.trade!!.pnlPrice!!, 0.000001)
        assertEquals(97.3, closed.trade.pnlUsd!!, 0.000001)
    }

    @Test
    fun invalidLotOrCommissionCannotOpenTrade() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4095.0, 4110.0, "TEST", now)
        val invalidLot = PaperTradingEngine.open(
            signal, quote(4099.8, 4100.2), now, 1.0, lotSize = 0.0
        )
        val invalidCommission = PaperTradingEngine.open(
            signal, quote(4099.8, 4100.2), now, 1.0, commissionPerLotRoundTurnUsd = -1.0
        )
        assertFalse(invalidLot.accepted)
        assertFalse(invalidCommission.accepted)
    }

    @Test
    fun spreadAdjustedRiskRewardCanRejectSmallTarget() {
        val signal = PaperSignal(TradeDirection.BUY, 4100.0, 4099.0, 4101.0, "REAL", now)
        val result = PaperTradingEngine.open(
            signal = signal,
            quote = quote(4099.8, 4100.2),
            nowMillis = now,
            maxEntryDistance = 1.0,
            minimumRiskReward = 1.0
        )
        assertFalse(result.accepted)
        assertNull(result.trade)
    }

}
