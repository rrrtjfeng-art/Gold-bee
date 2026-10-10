package com.goldbee.analysis

import com.goldbee.decision.TradeDirection
import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyBacktesterTest {

    @Test
    fun assumesStopLossFirstWhenBothLevelsTouchInSameCandleForBuy() {
        val candle = candle(high = 102.0, low = 98.0, open = 100.0, close = 101.0)
        assertEquals(
            BacktestExitType.STOP_LOSS,
            StrategyBacktester.resolveIntrabarExit(
                direction = TradeDirection.BUY,
                candle = candle,
                stopLoss = 99.0,
                takeProfit = 101.5
            )
        )
    }

    @Test
    fun assumesStopLossFirstWhenBothLevelsTouchInSameCandleForSell() {
        val candle = candle(high = 102.0, low = 98.0, open = 100.0, close = 99.0)
        assertEquals(
            BacktestExitType.STOP_LOSS,
            StrategyBacktester.resolveIntrabarExit(
                direction = TradeDirection.SELL,
                candle = candle,
                stopLoss = 101.0,
                takeProfit = 98.5
            )
        )
    }

    @Test
    fun detectsTargetOnlyWhenStopWasNotTouched() {
        val candle = candle(high = 102.0, low = 99.5, open = 100.0, close = 101.5)
        assertEquals(
            BacktestExitType.TAKE_PROFIT,
            StrategyBacktester.resolveIntrabarExit(
                direction = TradeDirection.BUY,
                candle = candle,
                stopLoss = 99.0,
                takeProfit = 101.0
            )
        )
    }

    @Test
    fun buyStopGapFillsAtWorseOpeningPrice() {
        val candle = candle(high = 100.5, low = 96.0, open = 97.0, close = 98.0)
        val fill = StrategyBacktester.resolveIntrabarExitFill(
            direction = TradeDirection.BUY,
            candle = candle,
            stopLoss = 99.0,
            takeProfit = 103.0
        )
        assertEquals(BacktestExitType.STOP_LOSS, fill?.first)
        assertEquals(97.0, fill?.second ?: Double.NaN, 0.0)
    }

    @Test
    fun sellStopGapFillsAtWorseOpeningPrice() {
        val candle = candle(high = 104.0, low = 101.5, open = 103.0, close = 102.5)
        val fill = StrategyBacktester.resolveIntrabarExitFill(
            direction = TradeDirection.SELL,
            candle = candle,
            stopLoss = 102.0,
            takeProfit = 98.0
        )
        assertEquals(BacktestExitType.STOP_LOSS, fill?.first)
        assertEquals(103.0, fill?.second ?: Double.NaN, 0.0)
    }

    @Test
    fun takeProfitGapDoesNotAssumePriceImprovement() {
        val candle = candle(high = 106.0, low = 100.5, open = 105.0, close = 105.5)
        val fill = StrategyBacktester.resolveIntrabarExitFill(
            direction = TradeDirection.BUY,
            candle = candle,
            stopLoss = 99.0,
            takeProfit = 103.0
        )
        assertEquals(BacktestExitType.TAKE_PROFIT, fill?.first)
        assertEquals(103.0, fill?.second ?: Double.NaN, 0.0)
    }

    @Test
    fun stopGapLossCanExceedOneR() {
        assertEquals(
            -3.0,
            StrategyBacktester.calculateRMultiple(
                direction = TradeDirection.BUY,
                entryPrice = 100.0,
                exitPrice = 97.0,
                risk = 1.0,
                exitType = BacktestExitType.STOP_LOSS,
                plannedReward = 2.0
            ),
            0.0
        )
        assertEquals(
            -3.0,
            StrategyBacktester.calculateRMultiple(
                direction = TradeDirection.SELL,
                entryPrice = 100.0,
                exitPrice = 103.0,
                risk = 1.0,
                exitType = BacktestExitType.STOP_LOSS,
                plannedReward = 2.0
            ),
            0.0
        )
    }

    @Test
    fun roundTripPriceCostReducesGrossRForWinsAndLosses() {
        assertEquals(
            1.7,
            StrategyBacktester.applyRoundTripCost(
                grossRMultiple = 2.0,
                risk = 1.0,
                roundTripCostPrice = 0.30
            ),
            0.000001
        )
        assertEquals(
            -1.3,
            StrategyBacktester.applyRoundTripCost(
                grossRMultiple = -1.0,
                risk = 1.0,
                roundTripCostPrice = 0.30
            ),
            0.000001
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRoundTripCost() {
        StrategyBacktester.applyRoundTripCost(
            grossRMultiple = 1.0,
            risk = 1.0,
            roundTripCostPrice = -0.01
        )
    }

    @Test
    fun emptyOrInsufficientHistoryCannotClaimStrategyPerformance() {
        val result = StrategyBacktester.run(emptyMap<Timeframe, List<Candle>>())
        assertTrue(result.trades.isEmpty())
        assertEquals(0.0, result.winRatePercent, 0.0)
        assertEquals(0.0, result.expectancyR, 0.0)
        assertNull(result.profitFactor)
        assertTrue(result.sampleIsTooSmall)
    }

    private fun candle(
        high: Double,
        low: Double,
        open: Double,
        close: Double
    ) = Candle(
        symbol = "XAUUSD",
        timeframe = Timeframe.M5,
        timestamp = 1_800_000_000L,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = 0.0
    )
}
