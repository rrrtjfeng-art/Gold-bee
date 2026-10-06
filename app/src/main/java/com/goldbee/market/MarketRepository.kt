package com.goldbee.market

class MarketRepository(
    private val provider: MarketDataProvider
) {

    private var latestSnapshot: MarketSnapshot? = null

    fun start(
        listener: MarketRepositoryListener? = null
    ) {

        provider.setListener(
            object : MarketDataListener {

                override fun onMarketUpdate(
                    snapshot: MarketSnapshot
                ) {
                    latestSnapshot = snapshot
                    listener?.onMarketUpdate(snapshot)
                }

                override fun onConnected() {
                    listener?.onConnected()
                }

                override fun onDisconnected() {
                    listener?.onDisconnected()
                }

                override fun onError(
                    message: String
                ) {
                    listener?.onError(message)
                }
            }
        )

        provider.connect()
    }

    fun stop() {
        provider.disconnect()
    }

    fun getLatestSnapshot(): MarketSnapshot? {
        return latestSnapshot
    }

    fun getCandles(
        timeframe: Timeframe
    ): List<Candle> {

        return latestSnapshot
            ?.candlesFor(timeframe)
            .orEmpty()
    }

    fun getLatestPrice(): Double? {
        return latestSnapshot?.midPrice
    }

    fun getSpread(): Double? {
        return latestSnapshot?.spread
    }

    fun isMarketReady(): Boolean {

        val snapshot =
            latestSnapshot
                ?: return false

        return snapshot.isPriceValid() &&
                snapshot.isFresh()
    }
}

interface MarketRepositoryListener {

    fun onMarketUpdate(
        snapshot: MarketSnapshot
    )

    fun onConnected()

    fun onDisconnected()

    fun onError(
        message: String
    )
}
