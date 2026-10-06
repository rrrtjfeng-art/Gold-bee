package com.goldbee.analysis

data class IndicatorSnapshot(
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val ema200: Double?,

    val rsi14: Double?,

    val macd: Double?,
    val macdSignal: Double?,
    val macdHistogram: Double?,

    val adx14: Double?,
    val atr14: Double?,

    val vwap: Double?
) {

    fun hasEnoughData(): Boolean {
        return ema20 != null &&
                ema50 != null &&
                rsi14 != null &&
                macd != null &&
                macdSignal != null &&
                adx14 != null &&
                atr14 != null &&
                vwap != null
    }
}
