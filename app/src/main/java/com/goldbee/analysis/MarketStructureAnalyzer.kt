package com.goldbee.analysis

import com.goldbee.market.Candle
import kotlin.math.abs

object MarketStructureAnalyzer {

fun analyze(
    candles: List<Candle>
): MarketStructure {

    if (candles.size < 10) {
        return MarketStructure(
            trend = Trend.SIDEWAYS,
            strength = 0.0,
            higherHigh = false,
            higherLow = false,
            lowerHigh = false,
            lowerLow = false,
            support = null,
            resistance = null,
            breakout = Breakout.NONE
        )
    }

    val sorted =
        candles.sortedBy { it.timestamp }

    val recent =
        sorted.takeLast(10)

    val previous =
        recent.take(5)

    val latest =
        recent.takeLast(5)

    val previousHigh =
        previous.maxOf { it.high }

    val previousLow =
        previous.minOf { it.low }

    val latestHigh =
        latest.maxOf { it.high }

    val latestLow =
        latest.minOf { it.low }

    val higherHigh =
        latestHigh > previousHigh

    val higherLow =
        latestLow > previousLow

    val lowerHigh =
        latestHigh < previousHigh

    val lowerLow =
        latestLow < previousLow

    val latestClose =
        latest.last().close

    val breakout =
        when {
            latestClose > previousHigh ->
                Breakout.BULLISH

            latestClose < previousLow ->
                Breakout.BEARISH

            else ->
                Breakout.NONE
        }

    val bullishPoints =
        listOf(
            higherHigh,
            higherLow
        ).count { it }

    val bearishPoints =
        listOf(
            lowerHigh,
            lowerLow
        ).count { it }

    val trend =
        when {
            bullishPoints > bearishPoints ->
                Trend.BULLISH

            bearishPoints > bullishPoints ->
                Trend.BEARISH

            else ->
                Trend.SIDEWAYS
        }

    val strength =
        when {
            trend == Trend.BULLISH ->
                bullishPoints / 2.0

            trend == Trend.BEARISH ->
                bearishPoints / 2.0

            else ->
                0.0
        }

    val support =
        findSupport(recent)

    val resistance =
        findResistance(recent)

    return MarketStructure(
        trend = trend,
        strength = strength,
        higherHigh = higherHigh,
        higherLow = higherLow,
        lowerHigh = lowerHigh,
        lowerLow = lowerLow,
        support = support,
        resistance = resistance,
        breakout = breakout
    )
}

private fun findSupport(
    candles: List<Candle>
): Double? {

    if (candles.isEmpty()) {
        return null
    }

    return candles
        .map { it.low }
        .minOrNull()
}

private fun findResistance(
    candles: List<Candle>
): Double? {

    if (candles.isEmpty()) {
        return null
    }

    return candles
        .map { it.high }
        .maxOrNull()
}

}
