package com.goldbee.decision

import com.goldbee.analysis.Breakout
import com.goldbee.analysis.IndicatorSnapshot
import com.goldbee.analysis.MarketStructure
import com.goldbee.analysis.MultiTimeframeAnalysis
import com.goldbee.analysis.TimeframeAnalysis
import com.goldbee.analysis.Trend
import com.goldbee.market.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopySignalEvaluatorTest {

    @Test
    fun blocksBuyWhenStopLossIsAboveEntry() {
        val signal = CopySignal(
            direction = TradeDirection.BUY,
            entry = 4110.0,
            stopLoss = 4115.0,
            takeProfit = 4125.0,
            rawText = "BUY ENTRY 4110 SL 4115 TP 4125"
        )

        val result = CopySignalEvaluator.evaluate(
            signal = signal,
            currentPrice = 4110.0,
            analysis = unavailableAnalysis(),
            candles = emptyMap(),
            spread = 0.2
        )

        assertEquals(DecisionAction.NO_TRADE, result.action)
        assertTrue(result.reason.contains("方向不匹配"))
    }

    @Test
    fun blocksBuyWhenOriginalTargetWasAlreadyReached() {
        val signal = CopySignal(
            direction = TradeDirection.BUY,
            entry = 4110.0,
            stopLoss = 4100.0,
            takeProfit = 4120.0,
            rawText = "BUY ENTRY 4110 SL 4100 TP 4120"
        )

        val result = CopySignalEvaluator.evaluate(
            signal = signal,
            currentPrice = 4120.1,
            analysis = unavailableAnalysis(),
            candles = emptyMap(),
            spread = 0.2
        )

        assertEquals(DecisionAction.NO_TRADE, result.action)
        assertTrue(result.reason.contains("TP"))
    }

    @Test
    fun blocksSignalWithoutExplicitEntry() {
        val signal = CopySignal(
            direction = TradeDirection.SELL,
            entry = null,
            stopLoss = 4120.0,
            takeProfit = 4090.0,
            rawText = "SELL SL 4120 TP 4090"
        )

        val result = CopySignalEvaluator.evaluate(
            signal = signal,
            currentPrice = 4110.0,
            analysis = unavailableAnalysis(),
            candles = emptyMap(),
            spread = 0.2
        )

        assertEquals(DecisionAction.NO_TRADE, result.action)
        assertTrue(result.reason.contains("Entry"))
    }

    private fun unavailableAnalysis(): MultiTimeframeAnalysis {
        fun frame(timeframe: Timeframe) = TimeframeAnalysis(
            timeframe = timeframe,
            indicators = IndicatorSnapshot(
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
            ),
            structure = MarketStructure(
                trend = Trend.SIDEWAYS,
                strength = 0.0,
                higherHigh = false,
                higherLow = false,
                lowerHigh = false,
                lowerLow = false,
                support = null,
                resistance = null,
                breakout = Breakout.NONE
            ),
            available = false
        )

        return MultiTimeframeAnalysis(
            m5 = frame(Timeframe.M5),
            m15 = frame(Timeframe.M15),
            h1 = frame(Timeframe.H1)
        )
    }
}
