package com.goldbee.analysis

import com.goldbee.market.Candle
import com.goldbee.market.Timeframe

/**
 * Small, transparent parameter search for research only.
 *
 * Parameters are selected on the chronological training segment, then evaluated
 * on a later holdout segment that was not used for selection. This is not machine
 * learning and does not guarantee predictive edge; it is a guarded grid search.
 */
data class StrategyTrainingResult(
    val selectedConfig: MultiTimeframeDecisionConfig,
    val training: BacktestResult,
    val validation: BacktestResult,
    /** Same untouched validation period, tested with twice the assumed round-trip cost. */
    val validationDoubleCost: BacktestResult,
    val candidatesEvaluated: Int,
    val splitTimeSeconds: Long,
    val selectionNote: String
)

object StrategyTrainer {
    private val targetRGrid = listOf(1.5, 2.0, 2.5)
    private val minimumScoreGrid = listOf(5, 6, 7, 8)

    fun trainAndValidate(
        sourceCandles: Map<Timeframe, List<Candle>>,
        roundTripCostPrice: Double,
        maxHoldBars: Int = 48,
        trainingFraction: Double = 0.70
    ): StrategyTrainingResult {
        require(trainingFraction in 0.60..0.80) {
            "Training fraction must be between 0.60 and 0.80"
        }
        require(roundTripCostPrice.isFinite() && roundTripCostPrice >= 0.0) {
            "Round-trip cost must be finite and non-negative"
        }
        StrategyBacktester.validateSourceCandles(sourceCandles)

        val m5 = sourceCandles[Timeframe.M5].orEmpty().sortedBy { it.timestamp }
        require(m5.size >= 180) {
            "至少需要 180 根 M5 K 线，才适合做训练段与样本外验证。"
        }
        require(listOf(Timeframe.M15, Timeframe.H1).all {
            sourceCandles[it].orEmpty().size >= 50
        }) {
            "M15 和 H1 各至少需要 50 根 K 线。"
        }

        val splitIndex = (m5.size * trainingFraction).toInt().coerceIn(1, m5.lastIndex)
        val splitTime = m5[splitIndex].timestamp
        val trainingCandles = sourceCandles.mapValues { (_, candles) ->
            candles.filter { it.timestamp < splitTime }
        }
        val baseline = MultiTimeframeDecisionConfig()
        val candidates = minimumScoreGrid.flatMap { score ->
            targetRGrid.map { target ->
                MultiTimeframeDecisionConfig(minimumScore = score, targetR = target)
            }
        }

        var evaluated = 0
        val scored = candidates.mapNotNull { config ->
            val result = StrategyBacktester.run(
                sourceCandles = trainingCandles,
                maxHoldBars = maxHoldBars,
                roundTripCostPrice = roundTripCostPrice,
                decisionConfig = config
            )
            evaluated++
            if (
                result.trades.size < 10 ||
                !result.expectancyR.isFinite() ||
                result.expectancyR <= 0.0 ||
                (result.profitFactor ?: 0.0) <= 1.0
            ) {
                null
            } else {
                // Penalize large historical drawdown slightly to avoid choosing
                // solely by a volatile high-expectancy training result.
                val researchScore = result.expectancyR - 0.05 * result.maxDrawdownR
                Triple(config, result, researchScore)
            }
        }.sortedByDescending { it.third }

        val chosen = scored.firstOrNull()
        val config = chosen?.first ?: baseline
        val trainingResult = chosen?.second ?: StrategyBacktester.run(
            sourceCandles = trainingCandles,
            maxHoldBars = maxHoldBars,
            roundTripCostPrice = roundTripCostPrice,
            decisionConfig = baseline
        )
        val validationResult = StrategyBacktester.run(
            sourceCandles = sourceCandles,
            maxHoldBars = maxHoldBars,
            roundTripCostPrice = roundTripCostPrice,
            decisionConfig = config,
            decisionStartTimeSeconds = splitTime
        )
        val validationDoubleCost = StrategyBacktester.run(
            sourceCandles = sourceCandles,
            maxHoldBars = maxHoldBars,
            roundTripCostPrice = roundTripCostPrice * 2.0,
            decisionConfig = config,
            decisionStartTimeSeconds = splitTime
        )
        val note = if (chosen == null) {
            "训练段没有候选参数同时满足至少 10 笔交易、净期望为正、Profit Factor 大于 1；保留默认参数，不声称已找到优势。"
        } else {
            "参数只按训练段中净期望为正且 Profit Factor 大于 1 的候选，再结合回撤惩罚选出；最终仍以未参与选择的样本外结果为准。"
        }

        return StrategyTrainingResult(
            selectedConfig = config,
            training = trainingResult,
            validation = validationResult,
            validationDoubleCost = validationDoubleCost,
            candidatesEvaluated = evaluated,
            splitTimeSeconds = splitTime,
            selectionNote = note
        )
    }
}
