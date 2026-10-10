package com.goldbee.analysis

import com.goldbee.market.Candle
import kotlin.math.abs
import kotlin.math.max

enum class SwingKind {
    SUPPORT,
    RESISTANCE
}

data class SwingPoint(
    val index: Int,
    val timestamp: Long,
    val price: Double,
    val kind: SwingKind,
    val confirmedAtIndex: Int
)

data class PriceZone(
    val kind: SwingKind,
    val low: Double,
    val high: Double,
    val touches: Int,
    val latestTimestamp: Long,
    val strengthScore: Double
) {
    val midpoint: Double
        get() = (low + high) / 2.0
}

data class SwingSupportResistanceResult(
    val swingHighs: List<SwingPoint>,
    val swingLows: List<SwingPoint>,
    val resistanceZones: List<PriceZone>,
    val supportZones: List<PriceZone>,
    val nearestResistance: PriceZone?,
    val nearestSupport: PriceZone?,
    val currentPrice: Double?
)

/**
 * Finds confirmed swing highs/lows and groups nearby pivots into horizontal zones.
 *
 * A pivot is only returned after [rightBars] later candles exist, avoiding the common
 * mistake of treating an unconfirmed local extreme as a confirmed swing.
 *
 * This is a TradingView-style pivot concept implemented from candle data; it does not
 * import TradingView's proprietary indicators or provide a TradingView market-data feed.
 */
object SwingSupportResistanceAnalyzer {

    fun analyze(
        candles: List<Candle>,
        leftBars: Int = 3,
        rightBars: Int = 3,
        currentPrice: Double? = candles.lastOrNull()?.close,
        atr: Double? = null,
        maximumPoints: Int = 12
    ): SwingSupportResistanceResult {
        require(leftBars >= 1) { "leftBars must be at least 1" }
        require(rightBars >= 1) { "rightBars must be at least 1" }
        require(maximumPoints >= 1) { "maximumPoints must be at least 1" }

        val sorted = candles.sortedBy { it.timestamp }
        if (sorted.size < leftBars + rightBars + 1) {
            return SwingSupportResistanceResult(
                swingHighs = emptyList(),
                swingLows = emptyList(),
                resistanceZones = emptyList(),
                supportZones = emptyList(),
                nearestResistance = null,
                nearestSupport = null,
                currentPrice = currentPrice?.takeIf { it.isFinite() && it > 0.0 }
            )
        }

        val pivots = mutableListOf<SwingPoint>()
        for (index in leftBars until sorted.size - rightBars) {
            val candidate = sorted[index]
            val left = sorted.subList(index - leftBars, index)
            val right = sorted.subList(index + 1, index + rightBars + 1)

            // On equal-high/low plateaus, keep the first extreme rather than duplicate it.
            val isSwingHigh = left.none { it.high >= candidate.high } &&
                right.none { it.high > candidate.high }
            val isSwingLow = left.none { it.low <= candidate.low } &&
                right.none { it.low < candidate.low }

            if (isSwingHigh) {
                pivots += SwingPoint(
                    index = index,
                    timestamp = candidate.timestamp,
                    price = candidate.high,
                    kind = SwingKind.RESISTANCE,
                    confirmedAtIndex = index + rightBars
                )
            }
            if (isSwingLow) {
                pivots += SwingPoint(
                    index = index,
                    timestamp = candidate.timestamp,
                    price = candidate.low,
                    kind = SwingKind.SUPPORT,
                    confirmedAtIndex = index + rightBars
                )
            }
        }

        val safePrice = currentPrice?.takeIf { it.isFinite() && it > 0.0 }
        val toleranceAtr = atr?.takeIf { it.isFinite() && it > 0.0 }?.times(0.30) ?: 0.0
        val allSupports = buildZones(
            pivots.filter { it.kind == SwingKind.SUPPORT },
            sorted.size,
            toleranceAtr
        )
        val allResistances = buildZones(
            pivots.filter { it.kind == SwingKind.RESISTANCE },
            sorted.size,
            toleranceAtr
        )

        val supports = allSupports
            .filter { safePrice == null || it.midpoint < safePrice }
            .sortedWith(compareByDescending<PriceZone> { it.midpoint }.thenByDescending { it.strengthScore })
        val resistances = allResistances
            .filter { safePrice == null || it.midpoint > safePrice }
            .sortedWith(compareBy<PriceZone> { it.midpoint }.thenByDescending { it.strengthScore })

        return SwingSupportResistanceResult(
            swingHighs = pivots.filter { it.kind == SwingKind.RESISTANCE }
                .sortedByDescending { it.index }.take(maximumPoints),
            swingLows = pivots.filter { it.kind == SwingKind.SUPPORT }
                .sortedByDescending { it.index }.take(maximumPoints),
            resistanceZones = resistances.take(maximumPoints),
            supportZones = supports.take(maximumPoints),
            nearestResistance = resistances.firstOrNull(),
            nearestSupport = supports.firstOrNull(),
            currentPrice = safePrice
        )
    }

    private fun buildZones(
        pivots: List<SwingPoint>,
        candleCount: Int,
        toleranceAtr: Double
    ): List<PriceZone> {
        if (pivots.isEmpty()) return emptyList()

        val sorted = pivots.sortedBy { it.price }
        val clusters = mutableListOf<MutableList<SwingPoint>>()
        for (pivot in sorted) {
            val current = clusters.lastOrNull()
            if (current == null) {
                clusters += mutableListOf(pivot)
                continue
            }

            val center = current.map { it.price }.average()
            val tolerance = max(abs(center) * 0.00025, toleranceAtr)
            if (abs(pivot.price - center) <= tolerance) {
                current += pivot
            } else {
                clusters += mutableListOf(pivot)
            }
        }

        return clusters.map { cluster ->
            val center = cluster.map { it.price }.average()
            val tolerance = max(abs(center) * 0.00025, toleranceAtr)
            val latest = cluster.maxBy { it.index }
            val age = (candleCount - 1 - latest.index).coerceAtLeast(0)
            val recencyBonus = 5.0 * (1.0 - age.toDouble() / max(candleCount, 1)).coerceIn(0.0, 1.0)
            PriceZone(
                kind = cluster.first().kind,
                low = cluster.minOf { it.price } - tolerance * 0.25,
                high = cluster.maxOf { it.price } + tolerance * 0.25,
                touches = cluster.size,
                latestTimestamp = latest.timestamp,
                strengthScore = cluster.size * 10.0 + recencyBonus
            )
        }.sortedByDescending { it.strengthScore }
    }
}
