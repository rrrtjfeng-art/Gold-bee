package com.goldbee.risk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskLimitsTest {

    @Test
    fun allowsTradeOnlyWhenAllRiskBoundariesPass() {
        assertTrue(
            RiskLimitChecker.canTrade(
                limits = RiskLimits(),
                state = RiskState(dailyLossPercent = 2.99, consecutiveLosses = 2),
                riskReward = 1.5
            )
        )
    }

    @Test
    fun blocksAtDailyLossLimit() {
        assertFalse(
            RiskLimitChecker.canTrade(
                RiskLimits(),
                RiskState(dailyLossPercent = 3.0),
                riskReward = 2.0
            )
        )
    }

    @Test
    fun blocksAtConsecutiveLossLimit() {
        assertFalse(
            RiskLimitChecker.canTrade(
                RiskLimits(),
                RiskState(consecutiveLosses = 3),
                riskReward = 2.0
            )
        )
    }

    @Test
    fun blocksInsufficientOrNonFiniteRiskReward() {
        assertFalse(RiskLimitChecker.canTrade(RiskLimits(), RiskState(), 1.49))
        assertFalse(RiskLimitChecker.canTrade(RiskLimits(), RiskState(), Double.NaN))
        assertFalse(RiskLimitChecker.canTrade(RiskLimits(), RiskState(), Double.POSITIVE_INFINITY))
    }

    @Test
    fun rejectsNonFiniteLimitsAndInvalidState() {
        assertFalse(RiskLimits(minimumRiskReward = Double.NaN).isValid())
        assertFalse(RiskLimits(maxDailyLossPercent = Double.POSITIVE_INFINITY).isValid())
        assertFalse(RiskLimitChecker.canTrade(RiskLimits(), RiskState(dailyLossPercent = Double.NaN), 2.0))
        assertFalse(RiskLimitChecker.canTrade(RiskLimits(), RiskState(consecutiveLosses = -1), 2.0))
    }
}
