package com.goldbee.market

import org.json.JSONObject

/**
 * 将已经确认格式的 JSON 行情消息转换为标准 Tick。
 *
 * 当前解析器只接受包含 symbol、bid、ask、timestamp 的消息。
 * 不会猜测供应商字段，也不会伪造价格。
 */
object MarketFeedMessageParser {

    fun parseTick(
        json: String,
        expectedSymbol: String,
        source: String
    ): MarketTick? {
        return try {
            val obj = JSONObject(json)

            val symbol = obj.optString("symbol")
            val bid = obj.optDouble("bid", Double.NaN)
            val ask = obj.optDouble("ask", Double.NaN)
            val timestamp = obj.optLong("timestamp", 0L)

            val tick = MarketTick(
                symbol = symbol,
                bid = bid,
                ask = ask,
                timestamp = timestamp,
                source = source
            )

            if (!tick.isValid()) return null
            if (!tick.isSymbol(expectedSymbol)) return null

            tick
        } catch (_: Exception) {
            null
        }
    }
}
