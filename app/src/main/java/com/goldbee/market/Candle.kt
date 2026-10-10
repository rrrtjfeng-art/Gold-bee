package com.goldbee.market

data class Candle(
    val symbol: String,
    val timeframe: Timeframe,

    val timestamp: Long,

    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,

    val volume: Double = 0.0
) {

    init {
        require(symbol.isNotBlank()) {
            "Symbol cannot be empty"
        }

        require(open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite()) {
            "OHLC prices must be finite"
        }

        require(volume.isFinite() && volume >= 0.0) {
            "Volume must be finite and non-negative"
        }

        require(open >= 0.0) {
            "Open price cannot be negative"
        }

        require(high >= 0.0) {
            "High price cannot be negative"
        }

        require(low >= 0.0) {
            "Low price cannot be negative"
        }

        require(close >= 0.0) {
            "Close price cannot be negative"
        }

        require(high >= low) {
            "High price cannot be lower than Low price"
        }

        require(high >= open) {
            "High price cannot be lower than Open price"
        }

        require(high >= close) {
            "High price cannot be lower than Close price"
        }

        require(low <= open) {
            "Low price cannot be higher than Open price"
        }

        require(low <= close) {
            "Low price cannot be higher than Close price"
        }
    }

    val isBullish: Boolean
        get() = close > open

    val isBearish: Boolean
        get() = close < open

    val isDoji: Boolean
        get() = close == open

    val bodySize: Double
        get() = kotlin.math.abs(close - open)

    val range: Double
        get() = high - low

    val upperWick: Double
        get() {
            return high - maxOf(open, close)
        }

    val lowerWick: Double
        get() {
            return minOf(open, close) - low
        }
}
