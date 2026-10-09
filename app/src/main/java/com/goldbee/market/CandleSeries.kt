package com.goldbee.market

/**
 * 管理单个品种、单个周期的 K 线序列。
 */
class CandleSeries(
    val symbol: String,
    val timeframe: Timeframe,
    private val maximumCandles: Int = 1000
) {
    private val candles = mutableListOf<Candle>()

    init {
        require(symbol.isNotBlank()) {
            "Symbol cannot be blank"
        }
        require(maximumCandles > 0) {
            "maximumCandles must be positive"
        }
    }

    @Synchronized
    fun replaceAll(items: List<Candle>) {
        require(items.all {
            it.symbol.equals(symbol, ignoreCase = true) &&
                it.timeframe == timeframe
        }) {
            "Candle symbol or timeframe mismatch"
        }

        candles.clear()

        items.sortedBy { it.timestamp }
            .distinctBy { it.timestamp }
            .takeLast(maximumCandles)
            .forEach { candles.add(it) }
    }

    @Synchronized
    fun append(candle: Candle): Boolean {
        if (!candle.symbol.equals(symbol, ignoreCase = true)) {
            return false
        }

        if (candle.timeframe != timeframe) {
            return false
        }

        val last = candles.lastOrNull()

        if (last != null && candle.timestamp < last.timestamp) {
            return false
        }

        if (last != null && candle.timestamp == last.timestamp) {
            candles[candles.lastIndex] = candle
            return true
        }

        candles.add(candle)

        while (candles.size > maximumCandles) {
            candles.removeAt(0)
        }

        return true
    }

    @Synchronized
    fun snapshot(): List<Candle> = candles.toList()

    @Synchronized
    fun latest(): Candle? = candles.lastOrNull()

    @Synchronized
    fun size(): Int = candles.size

    @Synchronized
    fun clear() {
        candles.clear()
    }
}
