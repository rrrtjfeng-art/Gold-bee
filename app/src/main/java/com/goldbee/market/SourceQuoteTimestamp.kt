package com.goldbee.market

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Parses only timestamps with an explicit time zone or numeric epoch.
 * A device receive time is not proof that the underlying market data is fresh.
 */
object SourceQuoteTimestamp {
    const val MAX_AGE_MILLIS: Long = 90_000L

    fun parseMillis(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null

        text.toLongOrNull()?.let { numeric ->
            return when {
                numeric in 1_000_000_000L..9_999_999_999L -> numeric * 1000L
                numeric in 1_000_000_000_000L..9_999_999_999_999L -> numeric
                else -> null
            }
        }

        return try {
            Instant.parse(text).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun isFresh(
        timestampMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): Boolean {
        if (timestampMillis == null || maxAgeMillis < 0L) return false
        val age = nowMillis - timestampMillis
        return age in 0L..maxAgeMillis
    }
}
