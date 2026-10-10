package com.goldbee.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketReconnectPolicyTest {

    @Test
    fun retriesTransientDisconnects() {
        assertTrue(WebSocketReconnectPolicy.shouldRetry())
        assertTrue(WebSocketReconnectPolicy.shouldRetry(closeCode = 1006))
        assertTrue(WebSocketReconnectPolicy.shouldRetry(errorCode = "temporary_unavailable"))
    }

    @Test
    fun doesNotRetryTerminalProviderFailures() {
        assertFalse(WebSocketReconnectPolicy.shouldRetry(closeCode = 4401))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(closeCode = 4403))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(closeCode = 4429))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(errorCode = "unauthenticated"))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(errorCode = "plan_gated"))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(errorCode = "too_many_connections"))
        assertFalse(WebSocketReconnectPolicy.shouldRetry(closeCode = 1000))
    }

    @Test
    fun backoffGrowsAndIsCappedWithBoundedJitter() {
        assertEquals(1_000L, WebSocketReconnectPolicy.delayMillis(0))
        assertEquals(2_000L, WebSocketReconnectPolicy.delayMillis(1))
        assertEquals(5_000L, WebSocketReconnectPolicy.delayMillis(2, 1_000L))
        assertEquals(64_000L, WebSocketReconnectPolicy.delayMillis(20, 9_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeAttempt() {
        WebSocketReconnectPolicy.delayMillis(-1)
    }
}
