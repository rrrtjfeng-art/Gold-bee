package com.goldbee.analysis

import com.goldbee.decision.DecisionAction
import com.goldbee.decision.TradeDirection
import com.goldbee.decision.TradeSetup
import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import kotlin.math.abs
import kotlin.math.max

enum class BacktestExitType {
    STOP_LOSS,
    TAKE_PROFIT,
    TIME_EXIT
}

data class BacktestTrade(
    val direction: TradeDirection,
    val signalTimeSeconds: Long,
    val entryTimeSeconds: Long,
    val exitTimeSeconds: Long,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val exitPrice: Double,
    /** Result before the configured round-trip cost assumption. */
    val grossRMultiple: Double,
    /** Result after subtracting the configured round-trip cost assumption. */
    val rMultiple: Double,
    val exitType: BacktestExitType
)

data class BacktestResult(
    val sampleBars: Int,
    val trades: List<BacktestTrade>,
    val wins: Int,
    val losses: Int,
    val winRatePercent: Double,
    /** Net R after the configured cost assumption. */
    val totalR: Double,
    /** Gross R before costs, useful for comparing the cost impact. */
    val grossTotalR: Double,
    /** Assumed round-trip transaction cost, expressed as equivalent XAUUSD price movement. */
    val roundTripCostPrice: Double,
    val expectancyR: Double,
    val profitFactor: Double?,
    val maxDrawdownR: Double,
    val sampleStartSeconds: Long?,
    val sampleEndSeconds: Long?
) {
    val sampleDurationDays: Double
        get() {
            val start = sampleStartSeconds ?: return 0.0
            val end = sampleEndSeconds ?: return 0.0
            return ((end - start).coerceAtLeast(0L)).toDouble() / 86400.0
        }

    val sampleIsTooSmall: Boolean
        get() = trades.size < 30 || sampleDurationDays < 7.0
}

/**
 * Walk-forward backtest of the current multi-timeframe decision engine.
 *
 * Each decision sees only candles whose close time is at or before the signal
 * time. Entries execute at the next M5 open. If stop and target are both touched
 * inside one candle, stop-loss is assumed first. A configurable round-trip
 * cost equivalent is subtracted from each trade in price units. This is a
 * simplified sensitivity assumption, not a reconstruction of broker fills.
 */
object StrategyBacktester {

    /**
     * Keeps only higher-timeframe candles that were fully closed by the
     * decision timestamp. Candle timestamps represent candle open times.
     */
    internal fun completedCandlesAtOrBefore(
        candles: List<Candle>,
        timeframe: Timeframe,
        decisionTimeSeconds: Long
    ): List<Candle> = candles.filter {
        it.timestamp + timeframe.seconds <= decisionTimeSeconds
    }

    internal fun validateSourceCandles(sourceCandles: Map<Timeframe, List<Candle>>) {
        sourceCandles.forEach { (timeframe, candles) ->
            require(candles.all { it.timeframe == timeframe }) {
                "Candle timeframe does not match map key: $timeframe"
            }
            require(candles.map { it.timestamp }.distinct().size == candles.size) {
                "Duplicate candle timestamps found for $timeframe"
            }
        }
        val symbols = sourceCandles.values.flatten()
            .map { it.symbol.trim().uppercase() }
            .distinct()
        require(symbols.size <= 1) {
            "Backtest data must contain only one symbol; found: ${symbols.joinToString()}"
        }
    }

    fun run(
        sourceCandles: Map<Timeframe, List<Candle>>,
        maxHoldBars: Int = 48,
        roundTripCostPrice: Double = 0.0,
        decisionConfig: MultiTimeframeDecisionConfig = MultiTimeframeDecisionConfig(),
        decisionStartTimeSeconds: Long? = null,
        decisionEndTimeSeconds: Long? = null
    ): BacktestResult {
        require(maxHoldBars > 0) { "maxHoldBars must be greater than zero" }
        validateSourceCandles(sourceCandles)
        require(roundTripCostPrice.isFinite() && roundTripCostPrice >= 0.0) {
            "Round-trip cost must be finite and non-negative"
        }

        val m5 = sourceCandles[Timeframe.M5].orEmpty().sortedBy { it.timestamp }
        val m15 = sourceCandles[Timeframe.M15].orEmpty().sortedBy { it.timestamp }
        val h1 = sourceCandles[Timeframe.H1].orEmpty().sortedBy { it.timestamp }
        val trades = mutableListOf<BacktestTrade>()

        if (m5.size < 51 || m15.size < 50 || h1.size < 50) {
            return result(m5.size, trades, m5.firstOrNull()?.timestamp, m5.lastOrNull()?.timestamp, roundTripCostPrice)
        }

        var signalIndex = 0
        while (signalIndex < m5.lastIndex) {
            val signalCandle = m5[signalIndex]
            val decisionTime = signalCandle.timestamp + Timeframe.M5.seconds
            if (decisionStartTimeSeconds != null && decisionTime < decisionStartTimeSeconds) {
                signalIndex++
                continue
            }
            if (decisionEndTimeSeconds != null && decisionTime > decisionEndTimeSeconds) {
                break
            }

            val visibleM15 = completedCandlesAtOrBefore(
                candles = m15,
                timeframe = Timeframe.M15,
                decisionTimeSeconds = decisionTime
            )
            val visibleH1 = completedCandlesAtOrBefore(
                candles = h1,
                timeframe = Timeframe.H1,
                decisionTimeSeconds = decisionTime
            )
            val visibleM5 = m5.take(signalIndex + 1)

            if (visibleM5.size < 50 || visibleM15.size < 50 || visibleH1.size < 50) {
                signalIndex++
                continue
            }

            val visible = mapOf(
                Timeframe.M5 to visibleM5,
                Timeframe.M15 to visibleM15,
                Timeframe.H1 to visibleH1
            )
            val analysis = MultiTimeframeAnalyzer.analyze(visible)
            val decision = MultiTimeframeDecisionEngine.decide(visible, analysis, decisionConfig)
            val setup = decision.setup
            if (
                (decision.action != DecisionAction.BUY && decision.action != DecisionAction.SELL) ||
                setup == null
            ) {
                signalIndex++
                continue
            }

            val entryIndex = signalIndex + 1
            val entryCandle = m5[entryIndex]
            val entry = entryCandle.open
            if (!setupValidAtEntry(setup, entry)) {
                signalIndex++
                continue
            }

            val risk = abs(entry - setup.stopLoss)
            val reward = abs(setup.takeProfit - entry)
            if (!risk.isFinite() || !reward.isFinite() || risk <= 0.0 || reward / risk < 1.0) {
                signalIndex++
                continue
            }

            val lastScanIndex = minOf(m5.lastIndex, entryIndex + maxHoldBars - 1)
            var exitIndex = lastScanIndex
            var exitPrice = m5[lastScanIndex].close
            var exitType = BacktestExitType.TIME_EXIT

            for (index in entryIndex..lastScanIndex) {
                val hit = resolveIntrabarExitFill(
                    direction = setup.direction,
                    candle = m5[index],
                    stopLoss = setup.stopLoss,
                    takeProfit = setup.takeProfit
                )
                if (hit != null) {
                    exitIndex = index
                    exitType = hit.first
                    exitPrice = hit.second
                    break
                }
            }

            val grossRMultiple = calculateRMultiple(
                direction = setup.direction,
                entryPrice = entry,
                exitPrice = exitPrice,
                risk = risk,
                exitType = exitType,
                plannedReward = reward
            )
            val netRMultiple = applyRoundTripCost(
                grossRMultiple = grossRMultiple,
                risk = risk,
                roundTripCostPrice = roundTripCostPrice
            )

            trades += BacktestTrade(
                direction = setup.direction,
                signalTimeSeconds = signalCandle.timestamp,
                entryTimeSeconds = entryCandle.timestamp,
                exitTimeSeconds = m5[exitIndex].timestamp,
                entryPrice = entry,
                stopLoss = setup.stopLoss,
                takeProfit = setup.takeProfit,
                exitPrice = exitPrice,
                grossRMultiple = grossRMultiple,
                rMultiple = netRMultiple,
                exitType = exitType
            )
            // One position at a time: do not open a new trade before this one exits.
            signalIndex = exitIndex + 1
        }

        val reportCandles = m5.filter { candle ->
            decisionStartTimeSeconds == null ||
                candle.timestamp + Timeframe.M5.seconds >= decisionStartTimeSeconds
        }
        return result(
            reportCandles.size,
            trades,
            reportCandles.firstOrNull()?.timestamp,
            reportCandles.lastOrNull()?.timestamp,
            roundTripCostPrice
        )
    }

    internal fun applyRoundTripCost(
        grossRMultiple: Double,
        risk: Double,
        roundTripCostPrice: Double
    ): Double {
        require(grossRMultiple.isFinite()) { "Gross R must be finite" }
        require(risk.isFinite() && risk > 0.0) { "Risk must be finite and positive" }
        require(roundTripCostPrice.isFinite() && roundTripCostPrice >= 0.0) {
            "Round-trip cost must be finite and non-negative"
        }
        return grossRMultiple - roundTripCostPrice / risk
    }

    internal fun resolveIntrabarExit(
        direction: TradeDirection,
        candle: Candle,
        stopLoss: Double,
        takeProfit: Double
    ): BacktestExitType? =
        resolveIntrabarExitFill(direction, candle, stopLoss, takeProfit)?.first

    /**
     * Returns exit type and modeled fill price. A stop crossed by an opening gap
     * fills at the worse opening price rather than pretending it filled at the
     * requested stop. Targets retain the target price, avoiding optimistic gap
     * improvement. If both levels are touched intrabar, stop-loss wins.
     */
    internal fun resolveIntrabarExitFill(
        direction: TradeDirection,
        candle: Candle,
        stopLoss: Double,
        takeProfit: Double
    ): Pair<BacktestExitType, Double>? {
        val stopHit: Boolean
        val targetHit: Boolean
        val stopGap: Boolean
        when (direction) {
            TradeDirection.BUY -> {
                stopHit = candle.low <= stopLoss
                targetHit = candle.high >= takeProfit
                stopGap = candle.open < stopLoss
            }
            TradeDirection.SELL -> {
                stopHit = candle.high >= stopLoss
                targetHit = candle.low <= takeProfit
                stopGap = candle.open > stopLoss
            }
        }
        return when {
            stopHit -> BacktestExitType.STOP_LOSS to
                if (stopGap) candle.open else stopLoss
            targetHit -> BacktestExitType.TAKE_PROFIT to takeProfit
            else -> null
        }
    }

    internal fun calculateRMultiple(
        direction: TradeDirection,
        entryPrice: Double,
        exitPrice: Double,
        risk: Double,
        exitType: BacktestExitType,
        plannedReward: Double
    ): Double {
        require(risk.isFinite() && risk > 0.0) { "Risk must be finite and positive" }
        val signedMove = when (direction) {
            TradeDirection.BUY -> exitPrice - entryPrice
            TradeDirection.SELL -> entryPrice - exitPrice
        }
        return when (exitType) {
            // Use the actual modeled fill, so a gap through a stop can lose more than 1R.
            BacktestExitType.STOP_LOSS,
            BacktestExitType.TIME_EXIT -> signedMove / risk
            BacktestExitType.TAKE_PROFIT -> plannedReward / risk
        }
    }

    private fun setupValidAtEntry(setup: TradeSetup, entry: Double): Boolean {
        if (!entry.isFinite() || entry <= 0.0) return false
        return when (setup.direction) {
            TradeDirection.BUY -> setup.stopLoss < entry && setup.takeProfit > entry
            TradeDirection.SELL -> setup.stopLoss > entry && setup.takeProfit < entry
        }
    }

    private fun result(
        sampleBars: Int,
        trades: List<BacktestTrade>,
        start: Long?,
        end: Long?,
        roundTripCostPrice: Double
    ): BacktestResult {
        val wins = trades.count { it.rMultiple > 0.0 }
        val losses = trades.count { it.rMultiple < 0.0 }
        val totalR = trades.sumOf { it.rMultiple }
        val netProfit = trades.filter { it.rMultiple > 0.0 }.sumOf { it.rMultiple }
        val netLoss = -trades.filter { it.rMultiple < 0.0 }.sumOf { it.rMultiple }
        var equity = 0.0
        var peak = 0.0
        var maxDrawdown = 0.0
        trades.forEach { trade ->
            equity += trade.rMultiple
            peak = max(peak, equity)
            maxDrawdown = max(maxDrawdown, peak - equity)
        }
        return BacktestResult(
            sampleBars = sampleBars,
            trades = trades.toList(),
            wins = wins,
            losses = losses,
            winRatePercent = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            totalR = totalR,
            grossTotalR = trades.sumOf { it.grossRMultiple },
            roundTripCostPrice = roundTripCostPrice,
            expectancyR = if (trades.isEmpty()) 0.0 else totalR / trades.size,
            profitFactor = if (netLoss > 0.0) netProfit / netLoss else null,
            maxDrawdownR = maxDrawdown,
            sampleStartSeconds = start,
            sampleEndSeconds = end
        )
    }
}
