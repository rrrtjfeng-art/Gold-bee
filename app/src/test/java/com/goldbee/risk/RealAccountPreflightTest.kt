package com.goldbee.risk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealAccountPreflightTest {
    private fun validInput() = RealAccountPreflightInput(
        symbol = "XAUUSD",
        bid = 4100.0,
        ask = 4100.2,
        quoteAgeMillis = 300L,
        accountBalance = 100.0,
        accountEquity = 100.0,
        freeMargin = 50.0,
        brokerReportedRequiredMargin = 10.0,
        lotSize = 0.01,
        brokerMinimumLot = 0.01,
        brokerMaximumLot = 10.0,
        brokerLotStep = 0.01,
        estimatedStopLossMoney = 1.0,
        maximumRiskPercent = 2.0,
        entry = 4100.2,
        stopLoss = 4090.0,
        takeProfit = 4120.0,
        brokerStopDistanceVerified = true,
        brokerMarginVerified = true,
        userExplicitlyConfirmed = true
    )

    @Test fun acceptsFullyCheckedConfirmedOrder() {
        assertTrue(RealAccountPreflight.evaluate(validInput()).ready)
    }

    @Test fun rejectsStaleQuote() {
        assertFalse(RealAccountPreflight.evaluate(validInput().copy(quoteAgeMillis = 2_001L)).ready)
    }

    @Test fun rejectsRiskAboveConfiguredLimit() {
        val result = RealAccountPreflight.evaluate(validInput().copy(estimatedStopLossMoney = 3.0))
        assertFalse(result.ready)
        assertTrue(result.estimatedRiskPercent!! > 2.0)
    }

    @Test fun rejectsUnverifiedBrokerMargin() {
        assertFalse(RealAccountPreflight.evaluate(validInput().copy(brokerMarginVerified = false)).ready)
    }

    @Test fun rejectsWithoutExplicitUserConfirmation() {
        assertFalse(RealAccountPreflight.evaluate(validInput().copy(userExplicitlyConfirmed = false)).ready)
    }

    @Test fun rejectsInvalidLotStep() {
        assertFalse(RealAccountPreflight.evaluate(validInput().copy(lotSize = 0.015)).ready)
    }
}
