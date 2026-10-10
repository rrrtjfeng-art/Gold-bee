package com.goldbee.market

/**
 * Retry rules for the live quote WebSocket.
 *
 * Terminal provider errors need a user-side fix; retrying them wastes
 * connections and quota. Transient network failures use capped exponential
 * backoff plus jitter to avoid synchronized reconnect storms.
 */
object WebSocketReconnectPolicy {
    const val BASE_DELAY_MS: Long = 1_000L
    const val MAX_BACKOFF_MS: Long = 60_000L
    const val MAX_JITTER_MS: Long = 4_000L

    private val terminalCloseCodes = setOf(4401, 4403, 4429)
    private val terminalErrorCodes = setOf(
        "unauthenticated",
        "plan_gated",
        "too_many_connections"
    )

    fun shouldRetry(
        closeCode: Int? = null,
        errorCode: String? = null
    ): Boolean {
        if (closeCode in terminalCloseCodes) return false
        if (closeCode == 1000) return false
        if (errorCode?.trim()?.lowercase() in terminalErrorCodes) return false
        return true
    }

    fun delayMillis(
        attempt: Int,
        jitterMillis: Long = 0L
    ): Long {
        require(attempt >= 0) { "Reconnect attempt cannot be negative" }
        require(jitterMillis >= 0L) { "Jitter cannot be negative" }

        var backoff = BASE_DELAY_MS
        repeat(attempt.coerceAtMost(16)) {
            backoff = (backoff * 2L).coerceAtMost(MAX_BACKOFF_MS)
        }
        return backoff.coerceAtMost(MAX_BACKOFF_MS) +
            jitterMillis.coerceAtMost(MAX_JITTER_MS)
    }
}
