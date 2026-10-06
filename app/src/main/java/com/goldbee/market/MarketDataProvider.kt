package com.goldbee.market

interface MarketDataProvider {

    fun isConnected(): Boolean

    fun getLatestSnapshot(): MarketSnapshot?

    fun connect()

    fun disconnect()

    fun setListener(
        listener: MarketDataListener?
    )
}

interface MarketDataListener {

    fun onMarketUpdate(
        snapshot: MarketSnapshot
    )

    fun onConnected()

    fun onDisconnected()

    fun onError(
        message: String
    )
}
