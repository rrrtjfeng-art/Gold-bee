package com.goldbee.market

data class MarketSnapshot(
    val symbol: String,

    val bid: Double,
    val ask: Double,

    val timestamp: Long,

    val candles: Map<Timeframe, List<Candle>>,

    val source: String,

    val receivedAt: Long = System.currentTimeMillis()
) {

    val midPrice: Double
        get() = (bid + ask) / 2.0

    val spread: Double
        get() = ask - bid

    fun candlesFor(
        timeframe: Timeframe
    ): List<Candle> {
        return candles[timeframe].orEmpty()
    }

    fun latestCandle(
        timeframe: Timeframe
    ): Candle? {
        return candlesFor(timeframe).lastOrNull()
    }

    fun hasEnoughCandles(
        timeframe: Timeframe,
        minimum: Int
    ): Boolean {
        return candlesFor(timeframe).size >= minimum
    }

    fun isPriceValid(): Boolean {
        return bid > 0.0 &&
                ask > 0.0 &&
                ask >= bid
    }

    fun isFresh(
        maxAgeMillis: Long = 3000L
    ): Boolean {
        val age = System.currentTimeMillis() - receivedAt

        return age >= 0L && age <= maxAgeMillis
    }
}
