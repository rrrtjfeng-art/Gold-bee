package com.goldbee.market

/**
 * 把实时价格 Tick 聚合成指定周期的 OHLC K 线。
 *
 * Twelve Data WebSocket 提供实时价格，
 * 不直接提供 M1/M5/M15/H1 OHLC。
 *
 * 因此 Gold Bee 在本地维护 K 线。
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

    /**
     * 放入历史 K 线。
     */
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

    /**
     * 接收一个实时价格。
     *
     * 返回值：
     * true  = 当前周期刚刚收盘
     * false = 仍然在当前 K 线内
     */
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

                val newCandle = Candle(
                    symbol = symbol,
                    timeframe = timeframe,
                    timestamp = candleStart,
                    open = price,
                    high = price,
                    low = price,
                    close = price,
                    volume = 0.0
                )

                currentBars[timeframe] =
                    newCandle

            } else if (
                candleStart >
                current.timestamp
            ) {

                candles
                    .getOrPut(timeframe) {
                        mutableListOf()
                    }
                    .add(current)

                trimHistory(timeframe)

                val newCandle = Candle(
                    symbol = symbol,
                    timeframe = timeframe,
                    timestamp = candleStart,
                    open = price,
                    high = price,
                    low = price,
                    close = price,
                    volume = 0.0
                )

                currentBars[timeframe] =
                    newCandle

                anyClosed = true

            } else if (
                candleStart ==
                current.timestamp
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

    /**
     * 返回当前已经完成的 K 线。
     */
    @Synchronized
    fun getClosedCandles(
        timeframe: Timeframe
    ): List<Candle> {

        return candles[
            timeframe
        ]
            ?.toList()
            .orEmpty()
    }

    /**
     * 返回当前正在形成的 K 线。
     */
    @Synchronized
    fun getCurrentCandle(
        timeframe: Timeframe
    ): Candle? {

        return currentBars[
            timeframe
        ]
    }

    /**
     * 返回历史 + 当前 K 线。
     *
     * 当前 K 线不应该直接用于最终闭盘确认。
     */
    @Synchronized
    fun getCandlesIncludingCurrent(
        timeframe: Timeframe
    ): List<Candle> {

        val result =
            candles[
                timeframe
            ]
                ?.toMutableList()
                ?: mutableListOf()

        currentBars[
            timeframe
        ]?.let {
            result.add(it)
        }

        return result
    }

    /**
     * 获取最近一根已经收盘的 K 线。
     */
    @Synchronized
    fun latestClosedCandle(
        timeframe: Timeframe
    ): Candle? {

        return candles[
            timeframe
        ]?.lastOrNull()
    }

    private fun trimHistory(
        timeframe: Timeframe
    ) {

        val list =
            candles[
                timeframe
            ] ?: return

        val maximum =
            500

        if (list.size > maximum) {

            val removeCount =
                list.size - maximum

            repeat(removeCount) {
                list.removeAt(0)
            }
        }
    }
}
