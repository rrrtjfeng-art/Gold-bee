package com.goldbee.market

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional adapter for GoldPrice.dev's documented authenticated WebSocket.
 *
 * A valid API key with streaming entitlement is required. This class never
 * treats an open TCP/WebSocket connection as a ready market feed: it reports
 * connected only after the provider confirms the XAU subscription.
 *
 * The caller remains responsible for submitting ticks to MarketFeedController
 * and checking freshness before any actionable decision.
 */
class GoldPriceDevWebSocketClient(
    private val apiKey: String,
    private val listener: MarketTickListener,
    private val symbol: String = "XAUUSD",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {
    private val source = GoldPriceDevTickParser.SOURCE
    @Volatile private var socket: WebSocket? = null
    @Volatile private var subscriptionConfirmed = false

    @Synchronized
    fun connect(): Boolean {
        if (apiKey.isBlank()) {
            listener.onError(source, "未设置 GoldPrice.dev API Key")
            return false
        }
        if (socket != null) return true

        val request = Request.Builder()
            .url("wss://api.goldprice.dev/v1/stream")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject()
                    .put("action", "auth")
                    .put("api_key", apiKey)
                    .toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                subscriptionConfirmed = false
                synchronized(this@GoldPriceDevWebSocketClient) {
                    if (socket === webSocket) socket = null
                }
                listener.onDisconnected(source)
                listener.onError(source, t.message ?: "行情 WebSocket 连接失败")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                subscriptionConfirmed = false
                synchronized(this@GoldPriceDevWebSocketClient) {
                    if (socket === webSocket) socket = null
                }
                listener.onDisconnected(source)
                if (reason.isNotBlank()) listener.onError(source, "行情连接关闭：$reason")
            }
        })
        return true
    }

    @Synchronized
    fun disconnect() {
        val current = socket
        socket = null
        subscriptionConfirmed = false
        current?.close(1000, "User requested disconnect")
        listener.onDisconnected(source)
    }

    fun isConnected(): Boolean = subscriptionConfirmed

    private fun handleMessage(webSocket: WebSocket, raw: String) {
        val frame = try {
            JSONObject(raw)
        } catch (_: Exception) {
            listener.onError(source, "收到无法解析的行情消息")
            return
        }

        when (frame.optString("type")) {
            "welcome" -> {
                val subscribe = JSONObject()
                    .put("action", "subscribe")
                    .put("symbols", JSONArray().put(providerSymbol(symbol)))
                if (!webSocket.send(subscribe.toString())) {
                    listener.onError(source, "发送订阅请求失败")
                }
            }
            "subscribed" -> {
                val symbols = frame.optJSONArray("symbols") ?: JSONArray()
                val confirmed = (0 until symbols.length()).any {
                    normalizeSymbol(symbols.optString(it)) == normalizeSymbol(providerSymbol(symbol))
                }
                if (confirmed) {
                    subscriptionConfirmed = true
                    listener.onConnected(source)
                } else {
                    listener.onError(source, "行情服务未确认 XAUUSD 订阅")
                }
            }
            "tick" -> {
                val tick = GoldPriceDevTickParser.parse(raw, symbol)
                if (tick == null) {
                    listener.onError(source, "行情数据缺少有效 Bid/Ask、时间戳或品种不匹配")
                } else if (subscriptionConfirmed) {
                    listener.onTick(tick)
                }
            }
            "error" -> {
                val code = frame.optString("code", "unknown")
                val message = frame.optString("message", "行情服务返回错误")
                subscriptionConfirmed = false
                listener.onError(source, "$code：$message")
            }
        }
    }

    private fun providerSymbol(value: String): String =
        if (normalizeSymbol(value) == "XAUUSD") "XAU-USD-SPOT" else value

    private fun normalizeSymbol(value: String): String =
        value.trim().uppercase().replace("-", "").replace("_", "").removeSuffix("SPOT")
}
