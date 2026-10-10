package com.goldbee.market

import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Parses the documented GoldPrice.dev live stream frame.
 *
 * The provider may omit bid or ask. Gold Bee deliberately rejects such frames
 * instead of inventing a spread from the last price.
 */
object GoldPriceDevTickParser {
    const val SOURCE = "goldprice.dev WebSocket"

    fun parse(json: String, expectedSymbol: String = "XAUUSD"): MarketTick? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("type") != "tick") return null

            val providerSymbol = obj.optString("symbol")
            if (!isExpectedSymbol(providerSymbol, expectedSymbol)) return null

            val bid = obj.optString("bid").toDoubleOrNull() ?: return null
            val ask = obj.optString("ask").toDoubleOrNull() ?: return null
            val timestamp = parseTimestamp(obj) ?: return null

            MarketTick(
                symbol = expectedSymbol.trim().uppercase(),
                bid = bid,
                ask = ask,
                timestamp = timestamp,
                source = SOURCE
            ).takeIf { it.isValid() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isExpectedSymbol(providerSymbol: String, expectedSymbol: String): Boolean {
        val normalizedProvider = normalizeSymbol(providerSymbol)
        val normalizedExpected = normalizeSymbol(expectedSymbol)
        return normalizedProvider.isNotBlank() &&
            normalizedExpected.isNotBlank() &&
            normalizedProvider == normalizedExpected
    }

    private fun normalizeSymbol(symbol: String): String =
        symbol.trim().uppercase()
            .replace("-", "")
            .replace("_", "")
            .removeSuffix("SPOT")

    private fun parseTimestamp(obj: JSONObject): Long? {
        val isoTimestamp = obj.optString("computed_at")
        if (isoTimestamp.isNotBlank() && isoTimestamp != "null") {
            return try {
                Instant.parse(isoTimestamp).toEpochMilli().takeIf { it > 0L }
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val numericTimestamp = obj.optLong("timestamp", 0L)
        return numericTimestamp.takeIf { it > 0L }
    }
}
