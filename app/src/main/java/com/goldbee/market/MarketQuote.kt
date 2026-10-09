package com.goldbee.market

/**
 * Gold Bee 统一报价。
 *
 * 只描述当前报价，不负责连接行情服务器。
 */
data class MarketQuote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
    val source: String,
    val receivedAt: Long = System.currentTimeMillis()
) {

    val midPrice: Double
        get() = (bid + ask) / 2.0

    val spread: Double
        get() = ask - bid

    fun isValid(): Boolean {
        return symbol.isNotBlank() &&
            bid.isFinite() &&
            ask.isFinite() &&
            bid > 0.0 &&
            ask >= bid &&
            timestamp > 0L &&
            source.isNotBlank()
    }

    /**
     * 检查接收时间是否足够新鲜。
     */
    fun isFresh(
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 3000L
    ): Boolean {
        if (!isValid()) return false
        if (maxAgeMillis < 0L) return false

        val age = nowMillis - receivedAt

        return age >= 0L && age <= maxAgeMillis
    }

    /**
     * 转换为统一 Tick 数据。
     */
    fun toMarketTick(): MarketTick {
        return MarketTick(
            symbol = symbol,
            bid = bid,
            ask = ask,
            timestamp = timestamp,
            source = source
        )
    }

    companion object {

        fun fromTick(tick: MarketTick): MarketQuote {
            return MarketQuote(
                symbol = tick.symbol,
                bid = tick.bid,
                ask = tick.ask,
                timestamp = tick.timestamp,
                source = tick.source
            )
        }
    }
}
