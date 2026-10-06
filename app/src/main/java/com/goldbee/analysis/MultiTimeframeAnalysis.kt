package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe

data class TimeframeAnalysis(
    val timeframe: Timeframe,
    val indicators: IndicatorSnapshot,
    val structure: MarketStructure,
    val available: Boolean
)

data class MultiTimeframeAnalysis(
    val m5: TimeframeAnalysis,
    val m15: TimeframeAnalysis,
    val h1: TimeframeAnalysis
) {

    fun bullishCount(): Int {
        return listOf(m5, m15, h1).count {
            it.available &&
                    it.structure.trend == Trend.BULLISH
        }
    }

    fun bearishCount(): Int {
        return listOf(m5, m15, h1).count {
            it.available &&
                    it.structure.trend == Trend.BEARISH
        }
    }

    fun bullishAgreement(): Boolean {
        return bullishCount() >= 2 &&
                m15.structure.trend == Trend.BULLISH
    }

    fun bearishAgreement(): Boolean {
        return bearishCount() >= 2 &&
                m15.structure.trend == Trend.BEARISH
    }
}

object MultiTimeframeAnalyzer {

    fun analyze(
        candles: Map<Timeframe, List<Candle>>
    ): MultiTimeframeAnalysis {

        return MultiTimeframeAnalysis(
            m5 = analyzeTimeframe(
                Timeframe.M5,
                candles[Timeframe.M5].orEmpty()
            ),
            m15 = analyzeTimeframe(
                Timeframe.M15,
                candles[Timeframe.M15].orEmpty()
            ),
            h1 = analyzeTimeframe(
                Timeframe.H1,
                candles[Timeframe.H1].orEmpty()
            )
        )
    }

    private fun analyzeTimeframe(
        timeframe: Timeframe,
        candles: List<Candle>
    ): TimeframeAnalysis {

        if (candles.size < 50) {
            return TimeframeAnalysis(
                timeframe = timeframe,
                indicators =
                    IndicatorCalculator.calculate(candles),
                structure =
                    MarketStructureAnalyzer.analyze(candles),
                available = false
            )
        }

        val indicators =
            IndicatorCalculator.calculate(candles)

        val structure =
            MarketStructureAnalyzer.analyze(candles)

        return TimeframeAnalysis(
            timeframe = timeframe,
            indicators = indicators,
            structure = structure,
            available =
                indicators.hasEnoughData()
        )
    }
}
