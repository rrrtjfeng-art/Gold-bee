package com.goldbee.risk

import com.goldbee.decision.TradeDirection
import com.goldbee.decision.TradeSetup
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryPriceGuardTest {

    private val validBuySetup = TradeSetup(
        direction = TradeDirection.BUY,
        entry = 100.0,
        stopLoss = 99.0,
        takeProfit = 102.0,
        riskReward = 2.0,
        reason = "Unit test setup"
    )

    @Test
    fun acceptsPriceInsideMaximumEntryDistance() {
        val result = EntryPriceGuard.validate(
            currentPrice = 100.2,
            setup = validBuySetup,
            atr = 1.0
        )
        assertEquals(EntryValidationStatus.VALID, result.status)
    }

    @Test
    fun rejectsPriceTooFarFromPlannedEntry() {
        val result = EntryPriceGuard.validate(
            currentPrice = 100.5,
            setup = validBuySetup,
            atr = 1.0
        )
        assertEquals(EntryValidationStatus.TOO_FAR, result.status)
    }

    @Test
    fun rejectsNanPriceAndInvalidAtr() {
        assertEquals(
            EntryValidationStatus.INVALID,
            EntryPriceGuard.validate(Double.NaN, validBuySetup, 1.0).status
        )
        assertEquals(
            EntryValidationStatus.INVALID,
            EntryPriceGuard.validate(100.0, validBuySetup, Double.POSITIVE_INFINITY).status
        )
        assertEquals(
            EntryValidationStatus.INVALID,
            EntryPriceGuard.validate(100.0, validBuySetup, 1.0, Double.NaN).status
        )
    }
}
