package com.goldbee.market

/**
 * 保存 Gold Bee 当前会话中的历史 K 线。
 *
 * 此类只负责内存管理，不负责网络请求或持久化。
 */
class MarketHistory(
    private val symbol: String = "XAUUSD",
    private val maximumCandlesPerTimeframe: Int = 1000
) {
    private val series =
        mutableMapOf<Timeframe, CandleSeries>()

    init {
        require(symbol.isNotBlank()) {
            "Symbol cannot be blank"
        }

        Timeframe.entries.forEach { timeframe ->
            series[timeframe] = CandleSeries(
                symbol = symbol,
                timeframe = timeframe,
                maximumCandles = maximumCandlesPerTimeframe
            )
        }
    }

    @Synchronized
    fun load(
        timeframe: Timeframe,
        candles: List<Candle>
    ) {
        getSeries(timeframe).replaceAll(candles)
    }

    @Synchronized
    fun add(
        candle: Candle
    ): Boolean {
        if (!candle.symbol.equals(symbol, ignoreCase = true)) {
            return false
        }

        return getSeries(candle.timeframe).append(candle)
    }

    @Synchronized
    fun get(
        timeframe: Timeframe
    ): List<Candle> {
        return getSeries(timeframe).snapshot()
    }

    @Synchronized
    fun latest(
        timeframe: Timeframe
    ): Candle? {
        return getSeries(timeframe).latest()
    }

    @Synchronized
    fun clear() {
        series.values.forEach { it.clear() }
    }

    private fun getSeries(
        timeframe: Timeframe
    ): CandleSeries {
        return series.getOrPut(timeframe) {
            CandleSeries(
                symbol = symbol,
                timeframe = timeframe,
                maximumCandles = maximumCandlesPerTimeframe
            )
        }
    }
}
