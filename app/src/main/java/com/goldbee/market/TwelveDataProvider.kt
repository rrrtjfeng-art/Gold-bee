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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var listener: MarketDataListener? = null

    private var connected = false
    private var latestSnapshot: MarketSnapshot? = null

    override fun isConnected(): Boolean {
        return connected
    }

    override fun getLatestSnapshot(): MarketSnapshot? {
        return latestSnapshot
    }

    override fun setListener(
        listener: MarketDataListener?
    ) {
        this.listener = listener
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

        val request = Request.Builder()
            .url("wss://ws.twelvedata.com/v1/quotes/price")
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    connected = true

                    listener?.onConnected()

                    subscribe(webSocket)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    handleMessage(text)
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

        val message = JSONObject()
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

            val json = JSONObject(text)

            val event = json.optString(
                "event"
            )

            if (event == "price") {

                val price = json.optDouble(
                    "price",
                    Double.NaN
                )

                if (price.isNaN() || price <= 0.0) {
                    return
                }

                val timestamp = json.optLong(
                    "timestamp",
                    System.currentTimeMillis() / 1000L
                )

                val nowMillis =
                    System.currentTimeMillis()

                val candle = Candle(
                    symbol = symbol,
                    timeframe = Timeframe.M1,
                    timestamp = timestamp,
                    open = price,
                    high = price,
                    low = price,
                    close = price,
                    volume = 0.0
                )

                val snapshot = MarketSnapshot(
                    symbol = symbol,
                    bid = price,
                    ask = price,
                    timestamp = timestamp,
                    candles = mapOf(
                        Timeframe.M1 to listOf(
                            candle
                        )
                    ),
                    source = "Twelve Data WebSocket",
                    receivedAt = nowMillis
                )

                latestSnapshot = snapshot

                listener?.onMarketUpdate(
                    snapshot
                )
            }

            if (event == "error") {

                val message =
                    json.optString(
                        "message",
                        "Twelve Data error."
                    )

                listener?.onError(message)
            }

        } catch (e: Exception) {

            listener?.onError(
                "Invalid market data: ${e.message}"
            )
        }
    }
}
