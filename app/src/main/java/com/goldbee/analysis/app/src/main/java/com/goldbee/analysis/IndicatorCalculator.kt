package com.goldbee.analysis

import com.goldbee.market.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object IndicatorCalculator {

    fun calculate(
        candles: List<Candle>
    ): IndicatorSnapshot {

        if (candles.isEmpty()) {
            return emptySnapshot()
        }

        val sorted =
            candles.sortedBy {
                it.timestamp
            }

        val closes =
            sorted.map {
                it.close
            }

        val highs =
            sorted.map {
                it.high
            }

        val lows =
            sorted.map {
                it.low
            }

        return IndicatorSnapshot(

            ema9 =
                ema(
                    closes,
                    9
                ),

            ema20 =
                ema(
                    closes,
                    20
                ),

            ema50 =
                ema(
                    closes,
                    50
                ),

            ema200 =
                ema(
                    closes,
                    200
                ),

            rsi14 =
                rsi(
                    closes,
                    14
                ),

            macd =
                macd(
                    closes
                )?.first,

            macdSignal =
                macd(
                    closes
                )?.second,

            macdHistogram =
                macd(
                    closes
                )?.third,

            adx14 =
                adx(
                    highs,
                    lows,
                    closes,
                    14
                ),

            atr14 =
                atr(
                    highs,
                    lows,
                    closes,
                    14
                ),

            vwap =
                vwap(
                    sorted
                )
        )
    }

    private fun emptySnapshot():
        IndicatorSnapshot {

        return IndicatorSnapshot(
            ema9 = null,
            ema20 = null,
            ema50 = null,
            ema200 = null,
            rsi14 = null,
            macd = null,
            macdSignal = null,
            macdHistogram = null,
            adx14 = null,
            atr14 = null,
            vwap = null
        )
    }

    private fun ema(
        values: List<Double>,
        period: Int
    ): Double? {

        if (values.size < period) {
            return null
        }

        val multiplier =
            2.0 / (period + 1)

        var result =
            values
                .take(period)
                .average()

        for (
            index in period until values.size
        ) {

            result =
                (
                    values[index] - result
                ) * multiplier + result
        }

        return result
    }

    private fun rsi(
        closes: List<Double>,
        period: Int
    ): Double? {

        if (closes.size <= period) {
            return null
        }

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {

            val change =
                closes[i] - closes[i - 1]

            if (change >= 0.0) {
                gains += change
            } else {
                losses += abs(change)
            }
        }

        var averageGain =
            gains / period

        var averageLoss =
            losses / period

        for (
            i in period + 1 until closes.size
        ) {

            val change =
                closes[i] - closes[i - 1]

            val gain =
                max(
                    change,
                    0.0
                )

            val loss =
                max(
                    -change,
                    0.0
                )

            averageGain =
                (
                    averageGain *
                            (period - 1) +
                            gain
                ) / period

            averageLoss =
                (
                    averageLoss *
                            (period - 1) +
                            loss
                ) / period
        }

        if (averageLoss == 0.0) {
            return 100.0
        }

        val relativeStrength =
            averageGain / averageLoss

        return 100.0 -
                (
                    100.0 /
                            (
                                1.0 +
                                        relativeStrength
                            )
                )
    }

    private fun macd(
        closes: List<Double>
    ): Triple<Double, Double, Double>? {

        if (closes.size < 35) {
            return null
        }

        val fast =
            emaSeries(
                closes,
                12
            )

        val slow =
            emaSeries(
                closes,
                26
            )

        val macdSeries =
            mutableListOf<Double>()

        for (
            i in slow.indices
        ) {

            val correspondingFastIndex =
                i + (
                    fast.size -
                            slow.size
                    )

            if (
                correspondingFastIndex
                in fast.indices
            ) {

                macdSeries.add(
                    fast[
                        correspondingFastIndex
                    ] - slow[i]
                )
            }
        }

        if (macdSeries.size < 9) {
            return null
        }

        val signal =
            ema(
                macdSeries,
                9
            ) ?: return null

        val currentMacd =
            macdSeries.last()

        return Triple(
            currentMacd,
            signal,
            currentMacd - signal
        )
    }

    private fun emaSeries(
        values: List<Double>,
        period: Int
    ): List<Double> {

        if (values.size < period) {
            return emptyList()
        }

        val multiplier =
            2.0 / (period + 1)

        val result =
            mutableListOf<Double>()

        var current =
            values
                .take(period)
                .average()

        result.add(current)

        for (
            i in period until values.size
        ) {

            current =
                (
                    values[i] - current
                ) * multiplier + current

            result.add(current)
        }

        return result
    }

    private fun trueRange(
        high: Double,
        low: Double,
        previousClose: Double
    ): Double {

        return max(
            high - low,
            max(
                abs(
                    high - previousClose
                ),
                abs(
                    low - previousClose
                )
            )
        )
    }

    private fun atr(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int
    ): Double? {

        if (
            highs.size != lows.size ||
            lows.size != closes.size ||
            closes.size <= period
        ) {
            return null
        }

        val ranges =
            mutableListOf<Double>()

        for (
            i in 1 until closes.size
        ) {

            ranges.add(
                trueRange(
                    high = highs[i],
                    low = lows[i],
                    previousClose =
                        closes[i - 1]
                )
            )
        }

        if (ranges.size < period) {
            return null
        }

        var result =
            ranges
                .take(period)
                .average()

        for (
            i in period until ranges.size
        ) {

            result =
                (
                    result *
                            (period - 1) +
                            ranges[i]
                ) / period
        }

        return result
    }

    private fun adx(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int
    ): Double? {

        if (
            highs.size < period + 2 ||
            lows.size != highs.size ||
            closes.size != highs.size
        ) {
            return null
        }

        val tr =
            mutableListOf<Double>()

        val plusDm =
            mutableListOf<Double>()

        val minusDm =
            mutableListOf<Double>()

        for (
            i in 1 until highs.size
        ) {

            val previousHigh =
                highs[i - 1]

            val previousLow =
                lows[i - 1]

            val previousClose =
                closes[i - 1]

            tr.add(
                trueRange(
                    highs[i],
                    lows[i],
                    previousClose
                )
            )

            val upMove =
                highs[i] - previousHigh

            val downMove =
                previousLow - lows[i]

            plusDm.add(
                if (
                    upMove > downMove &&
                    upMove > 0.0
                ) {
                    upMove
                } else {
                    0.0
                }
            )

            minusDm.add(
                if (
                    downMove > upMove &&
                    downMove > 0.0
                ) {
                    downMove
                } else {
                    0.0
                }
            )
        }

        if (tr.size < period) {
            return null
        }

        var smoothedTr =
            tr.take(period).sum()

        var smoothedPlus =
            plusDm.take(period).sum()

        var smoothedMinus =
            minusDm.take(period).sum()

        val dx =
            mutableListOf<Double>()

        fun addDx() {

            if (smoothedTr == 0.0) {
                return
            }

            val plusDi =
                100.0 *
                        smoothedPlus /
                        smoothedTr

            val minusDi =
                100.0 *
                        smoothedMinus /
                        smoothedTr

            val denominator =
                plusDi + minusDi

            if (denominator == 0.0) {
                return
            }

            val value =
                100.0 *
                        abs(
                            plusDi -
                                    minusDi
                        ) /
                        denominator

            dx.add(value)
        }

        addDx()

        for (
            i in period until tr.size
        ) {

            smoothedTr =
                smoothedTr -
                        (
                            smoothedTr /
                                    period
                        ) +
                        tr[i]

            smoothedPlus =
                smoothedPlus -
                        (
                            smoothedPlus /
                                    period
                        ) +
                        plusDm[i]

            smoothedMinus =
                smoothedMinus -
                        (
                            smoothedMinus /
                                    period
                        ) +
                        minusDm[i]

            addDx()
        }

        if (dx.size < period) {
            return null
        }

        var adx =
            dx
                .take(period)
                .average()

        for (
            i in period until dx.size
        ) {

            adx =
                (
                    adx *
                            (period - 1) +
                            dx[i]
                ) / period
        }

        return adx
    }

    private fun vwap(
        candles: List<Candle>
    ): Double? {

        if (candles.isEmpty()) {
            return null
        }

        var totalPriceVolume =
            0.0

        var totalVolume =
            0.0

        candles.forEach { candle ->

            val typicalPrice =
                (
                    candle.high +
                            candle.low +
                            candle.close
                    ) / 3.0

            val volume =
                candle.volume

            /*
             * 对没有真实成交量的 XAU/USD 数据，
             * 不让 VWAP 直接变成无意义的 0。
             *
             * 使用 1 作为最小权重，
             * 仅作为当前数据源的参考 VWAP。
             */
            val weight =
                if (volume > 0.0) {
                    volume
                } else {
                    1.0
                }

            totalPriceVolume +=
                typicalPrice * weight

            totalVolume +=
                weight
        }

        if (totalVolume == 0.0) {
            return null
        }

        return totalPriceVolume /
                totalVolume
    }
}
