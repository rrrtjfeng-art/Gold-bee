package com.goldbee.market

/**
 * Gold Bee 行情入口控制器。
 *
 * 接收行情供应商送来的 Tick，
 * 统一交给 MarketTickProcessor。
 *
 * 这里不绑定任何供应商，
 * 也不执行交易订单。
 */
class MarketFeedController(
    private val symbol: String = "XAUUSD",
    private val processor: MarketTickProcessor =
        MarketTickProcessor(symbol)
) {

    @Volatile
    private var connectionState: MarketConnectionState =
        MarketConnectionState.disconnected(symbol)

    @Volatile
    private var listener: MarketFeedListener? = null

    fun setListener(
        listener: MarketFeedListener?
    ) {
        this.listener = listener
    }

    @Synchronized
    fun markConnecting(source: String) {
        connectionState = MarketConnectionState.connecting(
            symbol = symbol,
            source = source
        )

        listener?.onConnectionStateChanged(connectionState)
    }

    @Synchronized
    fun markConnected(
        source: String,
        updateTime: Long = System.currentTimeMillis()
    ) {
        connectionState = MarketConnectionState.connected(
            symbol = symbol,
            source = source,
            updateTime = updateTime
        )

        listener?.onConnectionStateChanged(connectionState)
    }

    @Synchronized
    fun markDisconnected(source: String) {
        connectionState = MarketConnectionState(
            status = MarketConnectionStatus.DISCONNECTED,
            symbol = symbol,
            source = source
        )

        listener?.onConnectionStateChanged(connectionState)
    }

    @Synchronized
    fun markReconnecting(source: String) {
        connectionState = MarketConnectionState.reconnecting(
            symbol = symbol,
            source = source
        )

        listener?.onConnectionStateChanged(connectionState)
    }

    @Synchronized
    fun markError(
        source: String,
        message: String
    ) {
        connectionState = MarketConnectionState.error(
            symbol = symbol,
            source = source,
            message = message
        )

        listener?.onConnectionStateChanged(connectionState)
    }

    fun submitTick(tick: MarketTick): Boolean {
        if (!tick.isSymbol(symbol)) {
            listener?.onTickRejected(
                tick,
                "行情品种不匹配：需要 $symbol"
            )
            return false
        }

        val accepted = processor.process(tick)

        if (accepted) {
            listener?.onTickAccepted(
                tick,
                processor.getCandles(Timeframe.M1)
            )
        }

        return accepted
    }

    fun getConnectionState(): MarketConnectionState =
        connectionState

    fun getLatestTick(): MarketTick? =
        processor.getLatestTick()

    fun getLatestPrice(): Double? =
        processor.getLatestPrice()

    fun getCandles(
        timeframe: Timeframe
    ): List<Candle> =
        processor.getCandles(timeframe)

    fun getProcessor(): MarketTickProcessor = processor
}

/**
 * 行情入口事件。
 */
interface MarketFeedListener {

    fun onConnectionStateChanged(
        state: MarketConnectionState
    )

    fun onTickAccepted(
        tick: MarketTick,
        candles: List<Candle>
    )

    fun onTickRejected(
        tick: MarketTick,
        reason: String
    )
}
