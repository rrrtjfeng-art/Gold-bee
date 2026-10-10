package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/**
 * Groups backtest trades by the Malaysia-local time when the signal appeared.
 * Session windows are intentionally broad research buckets, not exchange-defined
 * sessions; London/New York windows shift with daylight-saving time.
 */
data class SessionWindow(
    val id: String,
    val label: String,
    val startHourMy: Int,
    val endHourMy: Int
) {
    init {
        require(startHourMy in 0..23)
        require(endHourMy in 0..23 && endHourMy != startHourMy)
    }

    fun containsHour(hour: Int): Boolean =
        if (startHourMy < endHourMy) hour >= startHourMy && hour < endHourMy
        else hour >= startHourMy || hour < endHourMy
}

data class SessionPerformance(
    val session: SessionWindow,
    val calendarDays: Int,
    val trades: Int,
    val wins: Int,
    val winRatePercent: Double,
    val totalR: Double,
    val expectancyR: Double,
    val profitFactor: Double?,
    val maxDrawdownR: Double,
    val grossTotalR: Double
) {
    val needsMoreData: Boolean get() = trades < 30 || calendarDays < 7
}

object SessionPerformanceAnalyzer {
    private val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")

    // Broad MYT buckets. London/NY local opening times shift by one hour during DST.
    val defaultWindows = listOf(
        SessionWindow("ASIA", "亚洲时段", 7, 14),
        SessionWindow("LONDON", "伦敦活跃时段", 15, 20),
        SessionWindow("OVERLAP", "欧美重叠活跃时段", 20, 0),
        SessionWindow("NEW_YORK_LATE", "纽约后段", 0, 3)
    )

    fun analyze(
        sourceCandles: Map<Timeframe, List<Candle>>,
        roundTripCostPrice: Double,
        maxHoldBars: Int = 48,
        decisionConfig: MultiTimeframeDecisionConfig = MultiTimeframeDecisionConfig(),
        windows: List<SessionWindow> = defaultWindows
    ): List<SessionPerformance> {
        require(roundTripCostPrice.isFinite() && roundTripCostPrice >= 0.0)
        require(windows.map { it.id }.distinct().size == windows.size) {
            "Session IDs must be unique"
        }
        StrategyBacktester.validateSourceCandles(sourceCandles)
        val m5 = sourceCandles[Timeframe.M5].orEmpty().sortedBy { it.timestamp }
        require(m5.size >= 51) { "至少需要 51 根 M5 K 线。" }
        require(sourceCandles[Timeframe.M15].orEmpty().size >= 50) { "M15 至少需要 50 根 K 线。" }
        require(sourceCandles[Timeframe.H1].orEmpty().size >= 50) { "H1 至少需要 50 根 K 线。" }

        val firstDate = Instant.ofEpochSecond(m5.first().timestamp).atZone(malaysiaZone).toLocalDate()
        val lastDate = Instant.ofEpochSecond(m5.last().timestamp).atZone(malaysiaZone).toLocalDate()
        val dates = generateSequence(firstDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDate) }
            .toList()

        return windows.map { window ->
            val sessionTrades = mutableListOf<BacktestTrade>()
            var daysWithBars = 0
            dates.forEach { date ->
                val start = date.atTime(window.startHourMy, 0).atZone(malaysiaZone).toEpochSecond()
                val endDate = if (window.endHourMy <= window.startHourMy) date.plusDays(1) else date
                val end = endDate.atTime(window.endHourMy, 0).atZone(malaysiaZone).toEpochSecond()
                if (m5.any { it.timestamp + Timeframe.M5.seconds > start && it.timestamp < end }) {
                    daysWithBars++
                    val dayResult = StrategyBacktester.run(
                        sourceCandles = sourceCandles,
                        maxHoldBars = maxHoldBars,
                        roundTripCostPrice = roundTripCostPrice,
                        decisionConfig = decisionConfig,
                        decisionStartTimeSeconds = start,
                        decisionEndTimeSeconds = end - 1
                    )
                    sessionTrades += dayResult.trades.filter { trade ->
                        val hour = Instant.ofEpochSecond(trade.signalTimeSeconds)
                            .atZone(malaysiaZone).hour
                        window.containsHour(hour) &&
                            trade.signalTimeSeconds + Timeframe.M5.seconds >= start &&
                            trade.signalTimeSeconds + Timeframe.M5.seconds < end
                    }
                }
            }
            summarize(window, daysWithBars, sessionTrades)
        }
    }

    private fun summarize(
        window: SessionWindow,
        days: Int,
        trades: List<BacktestTrade>
    ): SessionPerformance {
        val wins = trades.count { it.rMultiple > 0.0 }
        val totalR = trades.sumOf { it.rMultiple }
        val positiveR = trades.filter { it.rMultiple > 0.0 }.sumOf { it.rMultiple }
        val negativeR = -trades.filter { it.rMultiple < 0.0 }.sumOf { it.rMultiple }
        var equity = 0.0
        var peak = 0.0
        var drawdown = 0.0
        trades.forEach {
            equity += it.rMultiple
            peak = max(peak, equity)
            drawdown = max(drawdown, peak - equity)
        }
        return SessionPerformance(
            session = window,
            calendarDays = days,
            trades = trades.size,
            wins = wins,
            winRatePercent = if (trades.isEmpty()) 0.0 else wins * 100.0 / trades.size,
            totalR = totalR,
            expectancyR = if (trades.isEmpty()) 0.0 else totalR / trades.size,
            profitFactor = if (negativeR > 0.0) positiveR / negativeR else null,
            maxDrawdownR = drawdown,
            grossTotalR = trades.sumOf { it.grossRMultiple }
        )
    }
}
