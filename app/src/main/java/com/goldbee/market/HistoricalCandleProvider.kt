package com.goldbee.market

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 从 Twelve Data REST API 获取真实历史 OHLC 数据。
 *
 * 用途：
 * 1. APP 启动时获取历史 K 线
 * 2. 为 EMA / RSI / MACD / ADX / ATR / VWAP 等指标提供数据
 * 3. WebSocket 只负责实时价格
 */
class HistoricalCandleProvider(
    private val apiKey: String,
    private val symbol: String = "XAU/USD"
) {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                10,
                TimeUnit.SECONDS
            )
            .readTimeout(
                20,
                TimeUnit.SECONDS
            )
            .build()

    /**
     * 获取指定周期的历史 K 线。
     *
     * 默认 500 根。
     */
    fun getHistoricalCandles(
        timeframe: Timeframe,
        outputSize: Int = 500
    ): Result<List<Candle>> {

        if (apiKey.isBlank()) {

            return Result.failure(
                IllegalArgumentException(
                    "Twelve Data API key is missing."
                )
            )
        }

        if (outputSize <= 0) {

            return Result.failure(
                IllegalArgumentException(
                    "outputSize must be greater than 0."
                )
            )
        }

        val safeOutputSize =
            outputSize.coerceAtMost(5000)

        return try {

            val url =
                buildUrl(
                    timeframe = timeframe,
                    outputSize = safeOutputSize
                )

            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .build()

            client
                .newCall(request)
                .execute()
                .use { response ->

                    if (!response.isSuccessful) {

                        return Result.failure(
                            IllegalStateException(
                                "Twelve Data HTTP error: " +
                                        response.code
                            )
                        )
                    }

                    val body =
                        response.body?.string()

                    if (body.isNullOrBlank()) {

                        return Result.failure(
                            IllegalStateException(
                                "Twelve Data returned an empty response."
                            )
                        )
                    }

                    parseResponse(
                        body = body,
                        timeframe = timeframe
                    )
                }

        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    private fun buildUrl(
        timeframe: Timeframe,
        outputSize: Int
    ): String {

        return "https://api.twelvedata.com/time_series" +
                "?symbol=$symbol" +
                "&interval=${timeframe.code}" +
                "&outputsize=$outputSize" +
                "&apikey=$apiKey"
    }

    private fun parseResponse(
        body: String,
        timeframe: Timeframe
    ): Result<List<Candle>> {

        val json =
            JSONObject(body)

        val status =
            json.optString("status")

        if (
            status.equals(
                "error",
                ignoreCase = true
            )
        ) {

            val message =
                json.optString(
                    "message",
                    "Twelve Data returned an error."
                )

            return Result.failure(
                IllegalStateException(message)
            )
        }

        val values =
            json.optJSONArray("values")

        if (values == null) {

            return Result.failure(
                IllegalStateException(
                    "Twelve Data response contains no values."
                )
            )
        }

        val meta =
            json.optJSONObject("meta")

        val exchangeTimezone =
            meta?.optString(
                "exchange_timezone",
                "UTC"
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "UTC"

        val result =
            mutableListOf<Candle>()

        val formatter =
            DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss"
            )

        for (
            index in
            0 until values.length()
        ) {

            val item =
                values.optJSONObject(index)
                    ?: continue

            val datetime =
                item.optString(
                    "datetime"
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

            val volume =
                item.optString(
                    "volume"
                ).toDoubleOrNull()
                    ?: 0.0

            if (
                datetime.isBlank() ||
                open == null ||
                high == null ||
                low == null ||
                close == null
            ) {
                continue
            }

            val timestamp =
                parseTimestamp(
                    datetime = datetime,
                    exchangeTimezone =
                        exchangeTimezone,
                    formatter = formatter
                )

            if (timestamp <= 0L) {
                continue
            }

            try {

                result.add(
                    Candle(
                        symbol = symbol,
                        timeframe = timeframe,
                        timestamp = timestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )

            } catch (_: IllegalArgumentException) {
                // 跳过非法 OHLC 数据
            }
        }

        if (result.isEmpty()) {

            return Result.failure(
                IllegalStateException(
                    "Twelve Data returned no valid candles."
                )
            )
        }

        /*
         * Twelve Data 历史数据通常按最新 → 最旧返回。
         *
         * Gold Bee 内部统一使用：
         * 最旧 → 最新
         */
        result.sortBy {
            it.timestamp
        }

        return Result.success(
            result
        )
    }

    private fun parseTimestamp(
        datetime: String,
        exchangeTimezone: String,
        formatter: DateTimeFormatter
    ): Long {

        return try {

            val localDateTime =
                LocalDateTime.parse(
                    datetime,
                    formatter
                )

            localDateTime
                .atZone(
                    ZoneId.of(
                        exchangeTimezone
                    )
                )
                .toEpochSecond()

        } catch (_: Exception) {

            try {

                LocalDateTime.parse(
                    datetime,
                    formatter
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
}
