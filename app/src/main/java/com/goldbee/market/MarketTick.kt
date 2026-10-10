package com.goldbee.market

/**
 * Gold Bee 标准行情 Tick。
 *
 * 用于统一不同行情供应商提供的价格数据。
 * 本文件不连接任何特定供应商。
 */
data class MarketTick(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    /** Unix 时间戳，单位为毫秒。 */
    val timestamp: Long,
    val source: String
) {

    /** 买卖中间价。 */
    val midPrice: Double
        get() = bid + (ask - bid) / 2.0

    /** 买卖价差。 */
    val spread: Double
        get() = ask - bid

    val timestampMillis: Long
        get() = timestamp

    /**
     * 验证 Tick 的基本结构。
     *
     * 时间新鲜度由 isFresh 单独检查；这里先拒绝非有限价格，
     * 防止 NaN / Infinity 进入后续指标、价差和风险计算。
     */
    fun isValid(): Boolean {
        return symbol.isNotBlank() &&
            bid.isFinite() &&
            ask.isFinite() &&
            bid > 0.0 &&
            ask > 0.0 &&
            ask >= bid &&
            (ask - bid).isFinite() &&
            timestamp > 0L &&
            source.isNotBlank()
    }

    /**
     * 检查行情是否足够新鲜。
     *
     * timestamp 必须使用 Unix 毫秒时间戳。未来时间戳一律拒绝，
     * 且先比较时间再计算年龄，避免 Long 溢出造成错误放行。
     */
    fun isFresh(
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = 3000L
    ): Boolean {
        if (!isValid() || maxAgeMillis < 0L) return false
        if (timestamp > nowMillis) return false

        return nowMillis - timestamp <= maxAgeMillis
    }

    /** 检查是否属于指定品种。 */
    fun isSymbol(expectedSymbol: String): Boolean {
        return expectedSymbol.isNotBlank() &&
            symbol.equals(expectedSymbol, ignoreCase = true)
    }
}
