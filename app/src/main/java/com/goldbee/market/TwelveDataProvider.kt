package com.goldbee.market

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TwelveDataProvider(
    private val apiKey: String,
    private val symbol: String = "XAU/USD"
) : MarketDataProvider {

    companion object {
        private const val MAX_SOURCE_QUOTE_AGE_SECONDS = 10L
        private const val MAX_FUTURE_TIMESTAMP_SECONDS = 3L
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                10,
                TimeUnit.SECONDS
            )
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .build()

    private var webSocket:
        WebSocket? = null

    private var listener:
        MarketDataListener? = null

    private var connected =
        false

    private var latestSnapshot:
        MarketSnapshot? = null

    private val aggregator =
        CandleAggregator(
            symbol = symbol
        )

    private val historicalProvider =
        HistoricalCandleProvider(
            apiKey = apiKey,
            symbol = symbol
        )

    override fun isConnected(): Boolean {
        return connected
    }

    override fun getLatestSnapshot():
        MarketSnapshot? {

        return latestSnapshot
    }

    override fun setListener(
        listener: MarketDataListener?
    ) {

        this.listener =
            listener
    }

    /**
     * 加载真实历史 K 线。
     *
     * 默认：
     * M5  = 500
     * M15 = 500
     * H1  = 500
     *
     * 这三个周期足够我们后面建立第一版指标系统。
     */
    fun loadHistoricalData(): Boolean {

        if (apiKey.isBlank()) {

            listener?.onError(
                "Twelve Data API key is missing."
            )

            return false
        }

        val timeframes =
            listOf(
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.H1
            )

        var allSuccessful =
            true

        timeframes.forEach { timeframe ->

            val result =
                historicalProvider
                    .getHistoricalCandles(
                        timeframe = timeframe,
                        outputSize = 500
                    )

            result
                .onSuccess { candles ->

                    aggregator
                        .setHistoricalCandles(
                            timeframe = timeframe,
                            historicalCandles =
                                candles
                        )
                }
                .onFailure { error ->

                    allSuccessful =
                        false

                    listener?.onError(
                        "Historical " +
                                "${timeframe.name} " +
                                "data failed: " +
                                (
                                    error.message
                                        ?: "Unknown error"
                                )
                    )
                }
        }

        publishSnapshot()

        return allSuccessful
    }

    /**
     * 获取历史 K 线。
     */
    fun getHistoricalCandles(
        timeframe: Timeframe
    ): List<Candle> {

        return aggregator
            .getClosedCandles(
                timeframe
            )
    }

    override fun connect() {

        if (apiKey.isBlank()) {

            listener?.onError(
                "Twelve Data API key is missing."
            )

            return
        }

        if (connected) {
            return
        }

        disconnect()

        val url =
            "wss://ws.twelvedata.com/v1/quotes/price" +
                    "?apikey=$apiKey"

        val request =
            Request.Builder()
                .url(url)
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        connected = true

                        listener?.onConnected()

                        subscribe(
                            webSocket
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        handleMessage(
                            text
                        )
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        connected = false

                        listener?.onDisconnected()

                        webSocket.close(
                            1000,
                            null
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        connected = false

                        listener?.onDisconnected()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        connected = false

                        listener?.onError(
                            t.message
                                ?: "WebSocket connection failed."
                        )

                        listener?.onDisconnected()
                    }
                }
            )
    }

    override fun disconnect() {

        connected = false

        webSocket?.close(
            1000,
            "Client disconnect"
        )

        webSocket = null
    }

    private fun subscribe(
        webSocket: WebSocket
    ) {

        val message =
            JSONObject()
                .put(
                    "action",
                    "subscribe"
                )
                .put(
                    "params",
                    JSONObject()
                        .put(
                            "symbols",
                            symbol
                        )
                )

        webSocket.send(
            message.toString()
        )
    }

    private fun handleMessage(
        text: String
    ) {

        try {

            val json =
                JSONObject(text)

            val event =
                json.optString(
                    "event"
                )

            if (
                event.equals(
                    "subscribe-status",
                    ignoreCase = true
                )
            ) {

                val status =
                    json.optString(
                        "status"
                    )

                if (
                    status.equals(
                        "error",
                        ignoreCase = true
                    )
                ) {

                    listener?.onError(
                        json.optString(
                            "message",
                            "Subscription failed."
                        )
                    )
                }

                return
            }

            if (
                !event.equals(
                    "price",
                    ignoreCase = true
                )
            ) {
                return
            }

            val price =
                json.optDouble(
                    "price",
                    Double.NaN
                )

            if (!price.isFinite() || price <= 0.0) {
                return
            }

            val nowSeconds =
                System.currentTimeMillis() / 1000L

            val timestamp =
                json.optLong(
                    "timestamp",
                    nowSeconds
                )

            if (timestamp <= 0L) {
                return
            }

            // Prevent an old upstream quote from being stamped as freshly received.
            val sourceAgeSeconds = nowSeconds - timestamp
            if (
                sourceAgeSeconds > MAX_SOURCE_QUOTE_AGE_SECONDS ||
                sourceAgeSeconds < -MAX_FUTURE_TIMESTAMP_SECONDS
            ) {
                return
            }

            /*
             * 实时 tick。
             *
             * Twelve Data WebSocket
             * 当前不提供 OHLC / bid / ask。
             *
             * 所以由 CandleAggregator
             * 在本地构建正在形成的 K 线。
             */
            aggregator.onPrice(
                price = price,
                timestampSeconds = timestamp
            )

            publishSnapshot(
                price = price,
                timestamp = timestamp
            )

        } catch (e: Exception) {

            listener?.onError(
                "Invalid Twelve Data message: " +
                        "${e.message}"
            )
        }
    }

    private fun publishSnapshot(
        price: Double? = null,
        timestamp: Long? = null
    ) {

        val currentSnapshot =
            latestSnapshot

        val currentPrice =
            price
                ?: currentSnapshot?.midPrice
                ?: return

        val currentTimestamp =
            timestamp
                ?: currentSnapshot?.timestamp
                ?: return

        val candles =
            mutableMapOf<
                Timeframe,
                List<Candle>
                >()

        Timeframe.entries.forEach {
            timeframe ->

            candles[timeframe] =
                aggregator
                    .getCandlesIncludingCurrent(
                        timeframe
                    )
        }

        val snapshot =
            MarketSnapshot(
                symbol = symbol,

                /*
                 * 当前 Twelve Data
                 * WebSocket 没有 bid/ask。
                 *
                 * 所以这里只作为
                 * reference price。
                 *
                 * 真正 MT5 执行前必须重新
                 * 获取 MT5 报价。
                 */
                bid = currentPrice,

                ask = currentPrice,

                timestamp = currentTimestamp,

                candles = candles,

                source =
                    "Twelve Data",

                receivedAt =
                    System.currentTimeMillis()
            )

        latestSnapshot =
            snapshot

        listener?.onMarketUpdate(
            snapshot
        )
    }
}
