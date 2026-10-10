package com.goldbee.market

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * Optional adapter for GoldPrice.dev's documented authenticated WebSocket.
 *
 * A valid API key with streaming entitlement is required. A connection is
 * considered ready only after the provider confirms the XAU subscription.
 * Transient disconnects are retried with exponential backoff and jitter;
 * invalid credentials, missing entitlement and connection-limit errors are
 * terminal until the user changes the key/plan or frees a connection.
 *
 * This class only receives market data. It never places or modifies orders.
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
    @Volatile private var userRequestedDisconnect = true
    @Volatile private var terminalFailure = false
    private var reconnectAttempt = 0
    private var reconnectFuture: ScheduledFuture<*>? = null
    private var handshakeTimeoutFuture: ScheduledFuture<*>? = null
    private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "GoldBee-WebSocket-Reconnect").apply { isDaemon = true }
    }

    @Synchronized
    fun connect(): Boolean {
        if (apiKey.isBlank()) {
            listener.onError(source, "未设置 GoldPrice.dev API Key")
            return false
        }
        userRequestedDisconnect = false
        terminalFailure = false
        reconnectAttempt = 0
        cancelReconnectLocked()
        if (socket != null) return true
        return openSocketLocked()
    }

    @Synchronized
    private fun openSocketLocked(): Boolean {
        if (socket != null) return true
        val request = Request.Builder()
            .url("wss://api.goldprice.dev/v1/stream")
            .build()

        return try {
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val sent = webSocket.send(
                        JSONObject()
                            .put("action", "auth")
                            .put("api_key", apiKey)
                            .toString()
                    )
                    if (!sent) {
                        listener.onError(source, "发送行情认证请求失败")
                        webSocket.close(1011, "Authentication frame could not be sent")
                    } else {
                        scheduleHandshakeTimeout(webSocket)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(webSocket, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleSocketEnded(
                        webSocket = webSocket,
                        closeCode = null,
                        httpStatusCode = response?.code,
                        errorMessage = response?.code?.let { "行情服务 HTTP $it" }
                            ?: t.message ?: "行情 WebSocket 连接失败"
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSocketEnded(
                        webSocket = webSocket,
                        closeCode = code,
                        errorMessage = reason.takeIf { it.isNotBlank() }?.let { "行情连接关闭：$it" }
                    )
                }
            })
            true
        } catch (error: Exception) {
            socket = null
            listener.onError(source, error.message ?: "无法创建行情 WebSocket")
            scheduleReconnect()
            // Keep the client instance reachable so the user can cancel retries.
            true
        }
    }

    @Synchronized
    fun disconnect() {
        userRequestedDisconnect = true
        terminalFailure = false
        cancelReconnectLocked()
        cancelHandshakeTimeoutLocked()
        val current = socket
        socket = null
        subscriptionConfirmed = false
        current?.close(1000, "User requested disconnect")
        reconnectScheduler.shutdownNow()
        listener.onDisconnected(source)
    }

    fun isConnected(): Boolean = subscriptionConfirmed && socket != null

    private fun handleSocketEnded(
        webSocket: WebSocket,
        closeCode: Int?,
        errorMessage: String?,
        httpStatusCode: Int? = null
    ) {
        val wasCurrent = synchronized(this) {
            if (socket !== webSocket) {
                false
            } else {
                socket = null
                subscriptionConfirmed = false
                cancelHandshakeTimeoutLocked()
                if (!WebSocketReconnectPolicy.shouldRetry(
                        closeCode = closeCode,
                        httpStatusCode = httpStatusCode
                    )
                ) {
                    terminalFailure = true
                }
                true
            }
        }

        // A callback from an obsolete socket must not override a newer connection.
        if (!wasCurrent) return
        listener.onDisconnected(source)
        if (errorMessage != null) listener.onError(source, errorMessage)
        scheduleReconnect(closeCode = closeCode, httpStatusCode = httpStatusCode)
    }

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
                    webSocket.close(1011, "Subscription frame could not be sent")
                }
            }
            "subscribed" -> {
                val symbols = frame.optJSONArray("symbols") ?: JSONArray()
                val confirmed = (0 until symbols.length()).any {
                    normalizeSymbol(symbols.optString(it)) == normalizeSymbol(providerSymbol(symbol))
                }
                if (confirmed) {
                    subscriptionConfirmed = true
                    synchronized(this) {
                        reconnectAttempt = 0
                        cancelReconnectLocked()
                        cancelHandshakeTimeoutLocked()
                    }
                    listener.onConnected(source)
                } else {
                    listener.onError(source, "行情服务未确认 XAUUSD 订阅")
                    webSocket.close(1011, "Expected XAUUSD subscription was not confirmed")
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
                val terminal = !WebSocketReconnectPolicy.shouldRetry(errorCode = code)
                synchronized(this) {
                    terminalFailure = terminal
                }
                listener.onError(source, "$code：$message")
                webSocket.close(
                    if (terminal) 1000 else 1011,
                    if (terminal) "Terminal provider error" else "Transient provider error"
                )
            }
        }
    }

    @Synchronized
    private fun scheduleReconnect(
        closeCode: Int? = null,
        httpStatusCode: Int? = null
    ) {
        if (
            userRequestedDisconnect ||
            terminalFailure ||
            !WebSocketReconnectPolicy.shouldRetry(
                closeCode = closeCode,
                httpStatusCode = httpStatusCode
            )
        ) return
        if (reconnectFuture?.isDone == false) return

        val jitter = ThreadLocalRandom.current().nextLong(
            0L,
            WebSocketReconnectPolicy.MAX_JITTER_MS + 1L
        )
        val delay = WebSocketReconnectPolicy.delayMillis(reconnectAttempt, jitter)
        reconnectAttempt += 1
        reconnectFuture = reconnectScheduler.schedule({
            synchronized(this@GoldPriceDevWebSocketClient) {
                reconnectFuture = null
                if (userRequestedDisconnect || terminalFailure || socket != null) return@schedule
                openSocketLocked()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun scheduleHandshakeTimeout(webSocket: WebSocket) {
        synchronized(this) {
            if (userRequestedDisconnect || socket !== webSocket || subscriptionConfirmed) return
            cancelHandshakeTimeoutLocked()
            handshakeTimeoutFuture = reconnectScheduler.schedule({
                val timedOut = synchronized(this@GoldPriceDevWebSocketClient) {
                    handshakeTimeoutFuture = null
                    socket === webSocket && !subscriptionConfirmed && !userRequestedDisconnect
                }
                if (timedOut) {
                    listener.onError(source, "行情连接超时：服务器未在 12 秒内确认订阅")
                    webSocket.close(1011, "Subscription confirmation timeout")
                }
            }, HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    private fun cancelHandshakeTimeoutLocked() {
        handshakeTimeoutFuture?.cancel(false)
        handshakeTimeoutFuture = null
    }

    @Synchronized
    private fun cancelReconnectLocked() {
        reconnectFuture?.cancel(false)
        reconnectFuture = null
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 12_000L
    }

    private fun providerSymbol(value: String): String =
        if (normalizeSymbol(value) == "XAUUSD") "XAU-USD-SPOT" else value

    private fun normalizeSymbol(value: String): String =
        value.trim().uppercase().replace("-", "").replace("_", "").removeSuffix("SPOT")
}
