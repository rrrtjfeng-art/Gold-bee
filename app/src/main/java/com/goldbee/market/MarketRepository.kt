package com.goldbee.market

class MarketRepository(
    private val provider: MarketDataProvider
) {

    private var latestSnapshot: MarketSnapshot? = null

    private var listener:
        MarketDataListener? = null

    init {

        provider.setListener(
            object : MarketDataListener {

                override fun onMarketUpdate(
                    snapshot: MarketSnapshot
                ) {

                    latestSnapshot = snapshot

                    listener?.onMarketUpdate(
                        snapshot
                    )
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

                    listener?.onError(
                        message
                    )
                }
            }
        )
    }

    fun connect() {
        provider.connect()
    }

    fun disconnect() {
        provider.disconnect()
    }

    fun isConnected(): Boolean {
        return provider.isConnected()
    }

    fun getLatestSnapshot():
        MarketSnapshot? {

        return latestSnapshot
    }

    fun setListener(
        listener: MarketDataListener?
    ) {

        this.listener = listener
    }
}
