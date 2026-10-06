package com.goldbee.market

/**
 * 把实时价格 Tick 聚合成指定周期的 OHLC K 线。
 *
 * Twelve Data WebSocket 提供实时价格，
 * Gold Bee 在本地维护实时 K 线。
 */
class CandleAggregator(
    private val symbol: String
) {

    private val candles =
        mutableMapOf<Timeframe, MutableList<Candle>>()

    private val currentBars =
        mutableMapOf<Timeframe, Candle>()

    init {
        Timeframe.entries.forEach { timeframe ->
            candles[timeframe] = mutableListOf()
        }
    }

    @Synchronized
    fun setHistoricalCandles(
        timeframe: Timeframe,
        historicalCandles: List<Candle>
    ) {

        val target =
            candles.getOrPut(timeframe) {
                mutableListOf()
            }

        target.clear()

        target.addAll(
            historicalCandles
                .sortedBy { it.timestamp }
        )

        currentBars.remove(timeframe)
    }

    @Synchronized
    fun onPrice(
        price: Double,
        timestampSeconds: Long
    ): Boolean {

        if (price <= 0.0) {
            return false
        }

        var anyClosed = false

        Timeframe.entries.forEach { timeframe ->

            val candleStart =
                timestampSeconds -
                    (timestampSeconds % timeframe.seconds)

            val current =
                currentBars[timeframe]

            if (current == null) {

                val historicalLast =
                    candles[timeframe]
                        ?.lastOrNull()

                if (
                    historicalLast != null &&
                    historicalLast.timestamp == candleStart
                ) {

                    currentBars[timeframe] =
                        historicalLast.copy(
                            high = maxOf(
                                historicalLast.high,
                                price
                            ),
                            low = minOf(
                                historicalLast.low,
                                price
                            ),
                            close = price
                        )

                    candles[timeframe]
                        ?.removeAt(
                            candles[timeframe]!!.lastIndex
                        )

                } else {

                    currentBars[timeframe] =
                        Candle(
                            symbol = symbol,
                            timeframe = timeframe,
                            timestamp = candleStart,
                            open = price,
                            high = price,
                            low = price,
                            close = price,
                            volume = 0.0
                        )
                }

            } else if (
                candleStart > current.timestamp
            ) {

                candles
                    .getOrPut(timeframe) {
                        mutableListOf()
                    }
                    .add(current)

                trimHistory(timeframe)

                currentBars[timeframe] =
                    Candle(
                        symbol = symbol,
                        timeframe = timeframe,
                        timestamp = candleStart,
                        open = price,
                        high = price,
                        low = price,
                        close = price,
                        volume = 0.0
                    )

                anyClosed = true

            } else if (
                candleStart == current.timestamp
            ) {

                currentBars[timeframe] =
                    current.copy(
                        high = maxOf(
                            current.high,
                            price
                        ),
                        low = minOf(
                            current.low,
                            price
                        ),
                        close = price
                    )
            }
        }

        return anyClosed
    }

    @Synchronized
    fun getClosedCandles(
        timeframe: Timeframe
    ): List<Candle> {

        return candles[timeframe]
            ?.toList()
            .orEmpty()
    }

    @Synchronized
    fun getCurrentCandle(
        timeframe: Timeframe
    ): Candle? {

        return currentBars[timeframe]
    }

    @Synchronized
    fun getCandlesIncludingCurrent(
        timeframe: Timeframe
    ): List<Candle> {

        val result =
            candles[timeframe]
                ?.toMutableList()
                ?: mutableListOf()

        currentBars[timeframe]?.let {
            result.add(it)
        }

        return result
    }

    @Synchronized
    fun latestClosedCandle(
        timeframe: Timeframe
    ): Candle? {

        return candles[timeframe]
            ?.lastOrNull()
    }

    private fun trimHistory(
        timeframe: Timeframe
    ) {

        val list =
            candles[timeframe]
                ?: return

        val maximum = 500

        if (list.size > maximum) {

            val removeCount =
                list.size - maximum

            repeat(removeCount) {
                list.removeAt(0)
            }
        }
    }
}
