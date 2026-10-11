package com.goldbee.market

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only client for the user's private Gold Bee MT5 relay.
 * Network calls are synchronous by design and must run on an IO executor.
 */
object Mt5BridgeRelayClient {
    fun fetchLatest(baseUrl: String, readToken: String): MarketTick {
        require(baseUrl.startsWith("https://", ignoreCase = true)) {
            "行情桥只接受 HTTPS 地址。"
        }
        require(readToken.length >= 16) {
            "读取令牌无效；请填写部署时设置的独立 read token。"
        }
        val normalized = baseUrl.trim().trimEnd('/')
        val connection = (URL("$normalized/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2500
            readTimeout = 2500
            setRequestProperty("Authorization", "Bearer $readToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            useCaches = false
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                val reason = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw IllegalStateException(
                    when (status) {
                        401 -> "行情桥读取令牌错误（HTTP 401）。"
                        404 -> "行情桥尚未收到 MT5 Tick（HTTP 404）。请检查桌面 MT5 EA。"
                        409 -> "行情桥里的报价已过期，拒绝使用。"
                        503 -> "行情桥服务端尚未配置 KV 或令牌。"
                        else -> "行情桥请求失败：HTTP $status " + reason.take(80)
                    }
                )
            }
            val json = JSONObject(body)
            if (!json.optBoolean("ok", false)) throw IllegalStateException("行情桥未返回有效报价。")
            val brokerSymbol = json.optString("symbol", "").trim()
            val isGold = Regex("(?i)^(XAUUSD(?:[._-]?[A-Z0-9]{1,8})?|XAU/USD(?:[._-]?[A-Z0-9]{1,8})?|GOLD(?:[._-]?[A-Z0-9]{1,8})?)$")
                .matches(brokerSymbol)
            if (!isGold) throw IllegalStateException("MT5 桥接品种不是可识别的黄金品种：$brokerSymbol")
            val tick = MarketTick(
                // Normalize broker suffixes for Gold Bee's XAUUSD feed controller.
                // The EA/relay still retains the exact broker symbol in its payload.
                symbol = "XAUUSD",
                bid = json.optDouble("bid", Double.NaN),
                ask = json.optDouble("ask", Double.NaN),
                timestamp = json.optLong("timestamp_ms", 0L),
                source = "MT5_BRIDGE:$brokerSymbol"
            )
            if (!tick.isValid()) throw IllegalStateException("行情桥报价字段无效。")
            if (!tick.isFresh(maxAgeMillis = 10000L)) {
                throw IllegalStateException("MT5 报价超过 10 秒或时间戳异常，已拒绝使用。")
            }
            return tick
        } finally {
            connection.disconnect()
        }
    }
}
