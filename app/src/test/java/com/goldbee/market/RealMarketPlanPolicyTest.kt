package com.goldbee.market

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealMarketPlanPolicyTest {

    @Test
    fun freePlanCannotProvideActionableSignals() {
        assertFalse(RealMarketApiPlan.FREE.supportsWebSocketStreaming)
        assertFalse(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.FREE))
        assertTrue(RealMarketPlanPolicy.blockReason(RealMarketApiPlan.FREE).contains("不提供 WebSocket"))
    }

    @Test
    fun supportedPlanStillNeedsAnActiveValidatedStream() {
        assertTrue(RealMarketApiPlan.PLUS.supportsWebSocketStreaming)
        assertFalse(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.PLUS))
        assertFalse(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.PLUS, liveStreamConnected = false))
        assertTrue(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.PLUS, liveStreamConnected = true))
    }

    @Test
    fun starterPlanDoesNotAllowStreamingSignals() {
        assertFalse(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.STARTER, liveStreamConnected = true))
    }

    @Test
    fun businessPlanWithoutConnectionRemainsBlocked() {
        assertFalse(RealMarketPlanPolicy.allowsActionableSignals(RealMarketApiPlan.BUSINESS))
        assertTrue(RealMarketPlanPolicy.blockReason(RealMarketApiPlan.BUSINESS).contains("尚未建立"))
    }
}
