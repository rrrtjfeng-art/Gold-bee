package com.goldbee.market

/**
 * 合并历史 K 线与更新后的 K 线。
 *
 * 同一时间戳的 K 线以较新的输入为准。
 */
object MarketHistoryMerger {

    fun merge(
        historical: List<Candle>,
        updates: List<Candle>,
        maximumCandles: Int = 1000
    ): List<Candle> {
        require(maximumCandles > 0) {
            "maximumCandles must be positive"
        }

        val allCandles = historical + updates

        if (allCandles.isEmpty()) {
            return emptyList()
        }

        val first = allCandles.first()

        require(allCandles.all {
            it.symbol.equals(first.symbol, ignoreCase = true) &&
                it.timeframe == first.timeframe
        }) {
            "Cannot merge different symbols or timeframes"
        }

        val byTimestamp = sortedMapOf<Long, Candle>()

        historical.forEach { candle ->
            byTimestamp[candle.timestamp] = candle
        }

        updates.forEach { candle ->
            byTimestamp[candle.timestamp] = candle
        }

        return byTimestamp.values
            .toList()
            .takeLast(maximumCandles)
    }
}
