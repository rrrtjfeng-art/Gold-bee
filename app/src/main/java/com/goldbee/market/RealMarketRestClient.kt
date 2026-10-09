package com.goldbee.market

import android.os.Handler
import android.os.Looper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RealMarketPrice(
    val symbol: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val bid: Double?,
    val ask: Double?,
    val volume: Double?,
    val openTime: String
) {
    val spread: Double?
        get() = if (bid != null && ask != null && ask >= bid) {
            ask - bid
        } else {
            null
        }
}

class RealMarketRestClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchPrice(
        apiKey: String,
        symbol: String = "XAUUSD",
        timeframe: String = "M1",
        callback: (Result<RealMarketPrice>) -> Unit
    ) {
        if (apiKey.isBlank()) {
            callback(Result.failure(IllegalArgumentException(
                "请先填写 API Key"
            )))
            return
        }

        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")

        val url =
            "https://api.realmarketapi.com/api/v1/price" +
            "?apiKey=$encodedKey" +
            "&symbolCode=$symbol" +
            "&timeFrame=$timeframe"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        http.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    callback(Result.failure(
                        IOException("网络连接失败：${e.message}")
                    ))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        mainHandler.post {
                            callback(Result.failure(
                                IOException(
                                    "行情接口返回 HTTP ${it.code}。请检查 API Key、套餐权限及接口参数。"
                                )
                            ))
                        }
                        return
                    }

                    try {
                        val json = JSONObject(body)
                        val price = parsePrice(json, symbol)

                        mainHandler.post {
                            callback(Result.success(price))
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            callback(Result.failure(
                                IOException(
                                    "无法解析行情响应：${e.message}"
                                )
                            ))
                        }
                    }
                }
            }
        })
    }

    private fun parsePrice(
        root: JSONObject,
        requestedSymbol: String
    ): RealMarketPrice {

        val candidates = mutableListOf<JSONObject>()
        candidates.add(root)

        listOf("data", "result", "candle").forEach { key ->
            root.optJSONObject(key)?.let {
                candidates.add(it)
            }
        }

        val obj = candidates.firstOrNull {
            findNumber(it, "ClosePrice", "closePrice", "close") != null
        } ?: throw IllegalStateException(
            "响应中没有 ClosePrice。请检查接口返回内容和套餐权限。"
        )

        fun required(vararg keys: String): Double =
            findNumber(obj, *keys)
                ?: throw IllegalStateException(
                    "响应缺少字段：${keys.first()}"
                )

        return RealMarketPrice(
            symbol = findString(
                obj,
                "SymbolCode",
                "symbolCode",
                "symbol"
            ) ?: requestedSymbol,

            open = required("OpenPrice", "openPrice", "open"),
            high = required("HighPrice", "highPrice", "high"),
            low = required("LowPrice", "lowPrice", "low"),
            close = required("ClosePrice", "closePrice", "close"),

            bid = findNumber(obj, "Bid", "bid"),
            ask = findNumber(obj, "Ask", "ask"),
            volume = findNumber(obj, "Volume", "volume"),

            openTime = findString(
                obj,
                "OpenTime",
                "openTime",
                "time"
            ) ?: "接口未返回时间"
        )
    }

    private fun findNumber(
        obj: JSONObject,
        vararg keys: String
    ): Double? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue

            val value = obj.optDouble(key, Double.NaN)

            if (value.isFinite()) return value
        }

        return null
    }

    private fun findString(
        obj: JSONObject,
        vararg keys: String
    ): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue

            val value = obj.optString(key, "")
            if (value.isNotBlank()) return value
        }

        return null
    }
}
