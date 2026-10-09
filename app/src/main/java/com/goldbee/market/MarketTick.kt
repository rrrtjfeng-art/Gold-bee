package com.goldbee.market

/**
 * Gold Bee 标准实时行情 Tick。
 *
 * 用于统一不同行情供应商提供的价格数据。
 * 本文件不连接任何特定供应商。
 */
data class MarketTick(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
    val source: String
) {

    /**
     * 买卖中间价。
     */
    val midPrice: Double
        get() = (bid + ask) / 2.0

    /**
     * 买卖价差。
     */
    val spread: Double
        get() = ask - bid

    /**
     * 行情时间，单位为毫秒。
     */
    val timestampMillis: Long
        get() = timestamp

    /**
     * 检查价格数据是否有效。
     */
    fun isValid(): Boolean {
        return symbol.isNotBlank() &&
                bid > 0.0 &&
                ask > 0.0 &&
                ask >= bid &&
                timestamp > 0L &&
                source.isNotBlank()
    }

    /**
     * 检查行情是否足够新鲜。
     *
     * timestamp 必须使用 Unix 毫秒时间戳。
     */
    fun isFresh(
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 3000L
    ): Boolean {
        if (!isValid()) return false
        if (maxAgeMillis < 0L) return false

        val age = nowMillis - timestamp

        return age >= 0L && age <= maxAgeMillis
    }

    /**
     * 检查是否属于指定品种。
     */
    fun isSymbol(expectedSymbol: String): Boolean {
        return symbol.equals(expectedSymbol, ignoreCase = true)
    }
}
