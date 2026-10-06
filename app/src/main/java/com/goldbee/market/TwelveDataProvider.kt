package com.goldbee.market

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class TwelveDataProvider(
    private val apiKey: String,
    private val symbol: String = "XAU/USD"
) : MarketDataProvider {

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

    private var webSocket: WebSocket? = null

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

        /*
         * Twelve Data 官方 WebSocket 地址：
         *
         * wss://ws.twelvedata.com/v1/quotes/price?apikey=YOUR_API_KEY
         */
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

            /*
             * Twelve Data 会发送：
             *
             * subscribe-status
             * price
             *
             * subscribe-status 只代表订阅状态。
             */
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

            if (
                price.isNaN() ||
                price <= 0.0
            ) {
                return
            }

            val timestamp =
                json.optLong(
                    "timestamp",
                    System.currentTimeMillis() / 1000L
                )

            /*
             * Twelve Data 当前 WebSocket
             * 提供的是实时 tick price。
             *
             * 它不会直接提供 bid / ask。
             *
             * 所以这里暂时把 price 作为
             * 当前市场参考价格。
             *
             * 真正 MT5 下单前，
             * 后面必须重新读取 MT5 的报价，
             * 不能拿这里的 price 直接执行。
             */
            aggregator.onPrice(
                price = price,
                timestampSeconds = timestamp
            )

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

                    bid = price,

                    ask = price,

                    timestamp = timestamp,

                    candles = candles,

                    source =
                        "Twelve Data WebSocket",

                    receivedAt =
                        System.currentTimeMillis()
                )

            latestSnapshot =
                snapshot

            listener?.onMarketUpdate(
                snapshot
            )

        } catch (e: Exception) {

            listener?.onError(
                "Invalid Twelve Data message: " +
                        "${e.message}"
            )
        }
    }

    /**
     * 把 Twelve Data 历史时间字符串
     * 转换为 Unix timestamp。
     *
     * 后面历史 K 线模块会使用。
     */
    private fun parseTimestamp(
        value: String
    ): Long {

        return try {

            Instant.parse(
                value
            ).epochSecond

        } catch (_: Exception) {

            try {

                LocalDateTime
                    .parse(
                        value,
                        DateTimeFormatter
                            .ofPattern(
                                "yyyy-MM-dd HH:mm:ss"
                            )
                    )
                    .toInstant(
                        ZoneOffset.UTC
                    )
                    .epochSecond

            } catch (_: Exception) {

                0L
            }
        }
    }

    /**
     * 解析历史 OHLC JSON。
     *
     * 暂时保留在 Provider 内部，
     * 下一阶段会把历史数据正式接入
     * MarketSnapshot。
     */
    private fun parseHistoricalValues(
        values: JSONArray,
        timeframe: Timeframe
    ): List<Candle> {

        val result =
            mutableListOf<Candle>()

        for (
            index in
            values.length() - 1 downTo 0
        ) {

            val item =
                values.optJSONObject(
                    index
                ) ?: continue

            val timestamp =
                parseTimestamp(
                    item.optString(
                        "datetime"
                    )
                )

            val open =
                item.optString(
                    "open"
                ).toDoubleOrNull()

            val high =
                item.optString(
                    "high"
                ).toDoubleOrNull()

            val low =
                item.optString(
                    "low"
                ).toDoubleOrNull()

            val close =
                item.optString(
                    "close"
                ).toDoubleOrNull()

            if (
                timestamp <= 0L ||
                open == null ||
                high == null ||
                low == null ||
                close == null
            ) {
                continue
            }

            result.add(
                Candle(
                    symbol = symbol,
                    timeframe = timeframe,
                    timestamp = timestamp,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume =
                        item.optString(
                            "volume"
                        ).toDoubleOrNull()
                            ?: 0.0
                )
            )
        }

        return result
    }
}
