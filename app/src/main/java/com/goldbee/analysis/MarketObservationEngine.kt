package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ObservedTrend { BULLISH, BEARISH, NEUTRAL }
enum class ObservedRegime { TRENDING, RANGING, EXPANDING_VOLATILITY, INSUFFICIENT_DATA }
enum class ObservedMomentum { ACCELERATING_UP, ACCELERATING_DOWN, FADING, MIXED }
enum class LevelEvent { BREAKOUT_UP, BREAKOUT_DOWN, NEAR_RESISTANCE, NEAR_SUPPORT, INSIDE_RANGE, NONE }

data class ObservedLevel(val price: Double, val touches: Int, val label: String)

data class TimeframeObservation(
    val timeframe: Timeframe,
    val candleCount: Int,
    val trend: ObservedTrend,
    val lastClose: Double?,
    val atr: Double?,
    val recentHigh: Double?,
    val recentLow: Double?
)

data class MarketObservation(
    val symbol: String,
    val observedAtMs: Long,
    val quoteAgeMs: Long?,
    val quoteFresh: Boolean,
    val currentPrice: Double?,
    val spread: Double?,
    val trend: ObservedTrend,
    val regime: ObservedRegime,
    val momentum: ObservedMomentum,
    val levelEvent: LevelEvent,
    val support: ObservedLevel?,
    val resistance: ObservedLevel?,
    val timeframes: Map<Timeframe, TimeframeObservation>,
    val reasons: List<String>,
    val dataQuality: String
)

/**
 * A deterministic market-observation layer for a human-readable trading workflow.
 *
 * This does not predict guaranteed outcomes and does not submit orders. It describes
 * observable conditions from supplied candles and quote metadata. Callers must provide
 * broker-matched candles and quote timestamps; stale or insufficient data is surfaced
 * explicitly rather than silently treated as a trade opportunity.
 */
object MarketObservationEngine {
    private const val MIN_CANDLES = 20
    private const val LEVEL_LOOKBACK = 48
    private const val ATR_PERIOD = 14
    private const val DEFAULT_MAX_QUOTE_AGE_MS = 5_000L

    fun observe(
        candlesByTimeframe: Map<Timeframe, List<Candle>>,
        currentPrice: Double?,
        bid: Double? = null,
        ask: Double? = null,
        quoteTimestampMs: Long?,
        nowMs: Long,
        symbol: String = "XAUUSD",
        maxQuoteAgeMs: Long = DEFAULT_MAX_QUOTE_AGE_MS
    ): MarketObservation {
        require(symbol.isNotBlank()) { "symbol cannot be blank" }
        require(nowMs > 0L) { "nowMs must be positive" }
        require(maxQuoteAgeMs >= 0L) { "maxQuoteAgeMs cannot be negative" }

        val validPrice = currentPrice?.takeIf { it.isFinite() && it > 0.0 }
        val validBid = bid?.takeIf { it.isFinite() && it > 0.0 }
        val validAsk = ask?.takeIf { it.isFinite() && it > 0.0 }
        val spread = if (validBid != null && validAsk != null && validAsk >= validBid) validAsk - validBid else null
        val quoteAge = quoteTimestampMs?.takeIf { it > 0L && it <= nowMs }?.let { nowMs - it }
        val fresh = validPrice != null && quoteAge != null && quoteAge <= maxQuoteAgeMs

        val observations = Timeframe.entries.associateWith { timeframe ->
            val candles = candlesByTimeframe[timeframe].orEmpty()
                .filter { it.symbol.equals(symbol, ignoreCase = true) }
                .sortedBy { it.timestamp }
            observeTimeframe(timeframe, candles)
        }

        val m15 = candlesByTimeframe[Timeframe.M15].orEmpty()
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
            .sortedBy { it.timestamp }
        val m5 = candlesByTimeframe[Timeframe.M5].orEmpty()
            .filter { it.symbol.equals(symbol, ignoreCase = true) }
            .sortedBy { it.timestamp }

        val usableM15 = m15.size >= MIN_CANDLES
        val usableM5 = m5.size >= MIN_CANDLES
        val trend = combineTrend(observations)
        val regime = classifyRegime(m15)
        val momentum = classifyMomentum(m5)
        val levels = if (usableM15 && validPrice != null) findLevels(m15, validPrice) else Pair(null, null)
        val event = classifyLevelEvent(validPrice, levels.first, levels.second, m15)

        val reasons = mutableListOf<String>()
        if (!fresh) reasons += "报价缺失或已过期；禁止把该快照当作实时进场依据。"
        if (!usableM5) reasons += "M5 历史不足 $MIN_CANDLES 根，短线触发条件不可确认。"
        if (!usableM15) reasons += "M15 历史不足 $MIN_CANDLES 根，支撑阻力和市场环境可信度有限。"
        if (spread == null) reasons += "未提供有效 Bid/Ask，无法核对当前点差成本。"
        if (trend == ObservedTrend.NEUTRAL) reasons += "多周期方向没有形成一致趋势。"
        if (regime == ObservedRegime.RANGING) reasons += "市场更接近区间状态，突破信号需要收盘确认并防范假突破。"
        if (event == LevelEvent.BREAKOUT_UP) reasons += "价格高于近期 M15 区间高点；需检查收盘确认及回踩，不应直接追价。"
        if (event == LevelEvent.BREAKOUT_DOWN) reasons += "价格低于近期 M15 区间低点；需检查收盘确认及反抽，不应直接追空。"
        if (event == LevelEvent.NEAR_SUPPORT) reasons += "价格接近近期支撑区；接近支撑本身不是 BUY 触发。"
        if (event == LevelEvent.NEAR_RESISTANCE) reasons += "价格接近近期阻力区；接近阻力本身不是 SELL 触发。"
        if (reasons.isEmpty()) reasons += "数据满足基础观察条件；仍需由交易决策门检查入场触发、风险收益比和仓位风险。"

        val enoughData = usableM5 && usableM15
        val quality = when {
            !fresh -> "STALE_OR_MISSING_QUOTE"
            !enoughData -> "INSUFFICIENT_HISTORY"
            spread == null -> "MISSING_BID_ASK"
            else -> "OBSERVATION_READY_NOT_A_TRADE_SIGNAL"
        }

        return MarketObservation(
            symbol = symbol,
            observedAtMs = nowMs,
            quoteAgeMs = quoteAge,
            quoteFresh = fresh,
            currentPrice = validPrice,
            spread = spread,
            trend = trend,
            regime = if (!enoughData) ObservedRegime.INSUFFICIENT_DATA else regime,
            momentum = momentum,
            levelEvent = event,
            support = levels.first,
            resistance = levels.second,
            timeframes = observations,
            reasons = reasons,
            dataQuality = quality
        )
    }

    private fun observeTimeframe(timeframe: Timeframe, candles: List<Candle>): TimeframeObservation {
        val recent = candles.takeLast(LEVEL_LOOKBACK)
        val closes = recent.map { it.close }
        val last = closes.lastOrNull()
        val baseline = closes.getOrNull(max(0, closes.lastIndex - min(10, closes.lastIndex)))
        val delta = if (last != null && baseline != null) last - baseline else 0.0
        val scale = recent.map { it.high - it.low }.averageOrNull() ?: 0.0
        val threshold = max(scale * 0.12, (last ?: 0.0) * 0.00005)
        val trend = when {
            recent.size < MIN_CANDLES -> ObservedTrend.NEUTRAL
            delta > threshold -> ObservedTrend.BULLISH
            delta < -threshold -> ObservedTrend.BEARISH
            else -> ObservedTrend.NEUTRAL
        }
        return TimeframeObservation(
            timeframe = timeframe,
            candleCount = candles.size,
            trend = trend,
            lastClose = last,
            atr = calculateAtr(recent),
            recentHigh = recent.takeIf { it.isNotEmpty() }?.maxOf { it.high },
            recentLow = recent.takeIf { it.isNotEmpty() }?.minOf { it.low }
        )
    }

    private fun combineTrend(observations: Map<Timeframe, TimeframeObservation>): ObservedTrend {
        val higher = listOf(Timeframe.H1, Timeframe.M15).mapNotNull { observations[it]?.trend }
        val usable = higher.filter { it != ObservedTrend.NEUTRAL }
        if (usable.size < 2 || usable.distinct().size != 1) return ObservedTrend.NEUTRAL
        return usable.first()
    }

    private fun classifyRegime(candles: List<Candle>): ObservedRegime {
        if (candles.size < MIN_CANDLES) return ObservedRegime.INSUFFICIENT_DATA
        val recent = candles.takeLast(20)
        val ranges = recent.map { it.high - it.low }.filter { it.isFinite() }
        if (ranges.size < 15) return ObservedRegime.INSUFFICIENT_DATA
        val recentRange = ranges.takeLast(5).average()
        val baselineRange = ranges.dropLast(5).average().takeIf { it > 0.0 } ?: return ObservedRegime.INSUFFICIENT_DATA
        if (recentRange / baselineRange >= 1.6) return ObservedRegime.EXPANDING_VOLATILITY
        val closeDelta = recent.last().close - recent.first().close
        val totalRange = recent.maxOf { it.high } - recent.minOf { it.low }
        return if (totalRange > 0.0 && abs(closeDelta) / totalRange >= 0.55) {
            ObservedRegime.TRENDING
        } else {
            ObservedRegime.RANGING
        }
    }

    private fun classifyMomentum(candles: List<Candle>): ObservedMomentum {
        if (candles.size < 6) return ObservedMomentum.MIXED
        val recent = candles.takeLast(3)
        val prior = candles.dropLast(3).takeLast(3)
        if (prior.size < 3) return ObservedMomentum.MIXED
        val recentMove = recent.last().close - recent.first().open
        val priorMove = prior.last().close - prior.first().open
        val scale = (recent + prior).map { it.high - it.low }.averageOrNull() ?: return ObservedMomentum.MIXED
        if (scale <= 0.0) return ObservedMomentum.MIXED
        if (recentMove > 0 && priorMove > 0 && recentMove > priorMove + scale * 0.25) return ObservedMomentum.ACCELERATING_UP
        if (recentMove < 0 && priorMove < 0 && recentMove < priorMove - scale * 0.25) return ObservedMomentum.ACCELERATING_DOWN
        if (abs(recentMove) < abs(priorMove) * 0.55) return ObservedMomentum.FADING
        return ObservedMomentum.MIXED
    }

    private fun findLevels(candles: List<Candle>, price: Double): Pair<ObservedLevel?, ObservedLevel?> {
        val sample = candles.dropLast(1).takeLast(LEVEL_LOOKBACK)
        if (sample.isEmpty()) return Pair(null, null)
        val atr = calculateAtr(sample) ?: return Pair(null, null)
        val tolerance = max(atr * 0.18, price * 0.00008)
        val highs = sample.map { it.high }.filter { it >= price - tolerance }
        val lows = sample.map { it.low }.filter { it <= price + tolerance }
        val resistancePrice = highs.filter { it > price }.minOrNull()
        val supportPrice = lows.filter { it < price }.maxOrNull()
        val resistance = resistancePrice?.let { level ->
            ObservedLevel(level, highs.count { abs(it - level) <= tolerance }, "近期阻力")
        }
        val support = supportPrice?.let { level ->
            ObservedLevel(level, lows.count { abs(it - level) <= tolerance }, "近期支撑")
        }
        return Pair(support, resistance)
    }

    private fun classifyLevelEvent(
        price: Double?,
        support: ObservedLevel?,
        resistance: ObservedLevel?,
        candles: List<Candle>
    ): LevelEvent {
        if (price == null || candles.size < MIN_CANDLES) return LevelEvent.NONE
        val prior = candles.dropLast(1).takeLast(20)
        if (prior.isEmpty()) return LevelEvent.NONE
        val high = prior.maxOf { it.high }
        val low = prior.minOf { it.low }
        val tolerance = max((high - low) * 0.04, price * 0.00005)
        return when {
            price > high -> LevelEvent.BREAKOUT_UP
            price < low -> LevelEvent.BREAKOUT_DOWN
            support != null && abs(price - support.price) <= tolerance -> LevelEvent.NEAR_SUPPORT
            resistance != null && abs(price - resistance.price) <= tolerance -> LevelEvent.NEAR_RESISTANCE
            else -> LevelEvent.INSIDE_RANGE
        }
    }

    private fun calculateAtr(candles: List<Candle>): Double? {
        if (candles.size < 2) return null
        val trueRanges = candles.zipWithNext().map { (previous, current) ->
            max(current.high - current.low, max(abs(current.high - previous.close), abs(current.low - previous.close)))
        }.filter { it.isFinite() && it >= 0.0 }
        return trueRanges.takeLast(ATR_PERIOD).takeIf { it.isNotEmpty() }?.average()
            ?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun List<Double>.averageOrNull(): Double? =
        takeIf { it.isNotEmpty() && all { value -> value.isFinite() } }?.average()
}
