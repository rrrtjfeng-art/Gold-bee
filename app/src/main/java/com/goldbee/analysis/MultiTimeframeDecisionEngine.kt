package com.goldbee.analysis

import com.goldbee.decision.DecisionAction
import com.goldbee.decision.DecisionResult
import com.goldbee.decision.TradeDirection
import com.goldbee.decision.TradeSetup
import com.goldbee.market.Candle
import com.goldbee.market.Timeframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MultiTimeframeDecisionConfig(
    val minimumScore: Int = 6,
    val targetR: Double = 1.8
) {
    init {
        require(minimumScore in 4..9) { "minimumScore must be between 4 and 9" }
        require(targetR.isFinite() && targetR in 1.2..3.0) { "targetR must be between 1.2 and 3.0" }
    }
}

/**
 * Deterministic, closed-candle XAUUSD scenario engine.
 * Confidence is a rule-confluence score, not a calibrated win probability.
 */
object MultiTimeframeDecisionEngine {
    private const val MIN_STOP_ATR = 0.65
    private const val MAX_STOP_ATR = 2.20
    private const val MAX_CHASE_ATR_M5 = 0.90
    private const val MAX_QUOTE_GAP_ATR_M5 = 0.55
    private const val MIN_NET_RR_BEFORE_COSTS = 1.25

    fun decide(
        candles: Map<Timeframe, List<Candle>>,
        analysis: MultiTimeframeAnalysis,
        config: MultiTimeframeDecisionConfig = MultiTimeframeDecisionConfig(),
        currentPrice: Double? = null
    ): DecisionResult {
        if (!analysis.m5.available || !analysis.m15.available || !analysis.h1.available) {
            return noTrade("M5、M15、H1 指标或历史数据不足；暂不生成交易计划。")
        }

        val m5 = candles[Timeframe.M5].orEmpty().sortedBy { it.timestamp }
        val m15 = candles[Timeframe.M15].orEmpty().sortedBy { it.timestamp }
        val h1 = candles[Timeframe.H1].orEmpty().sortedBy { it.timestamp }
        if (m5.size < 50 || m15.size < 50 || h1.size < 50) {
            return noTrade("闭合 K 线不足 50 根；不使用短历史强行预测。")
        }

        val price = currentPrice?.takeIf { it.isFinite() && it > 0.0 } ?: m5.last().close
        val atr5 = analysis.m5.indicators.atr14 ?: return noTrade("M5 ATR 不可用，无法判断追价距离。")
        val atr15 = analysis.m15.indicators.atr14 ?: return noTrade("M15 ATR 不可用，无法计算结构止损。")
        if (!atr5.isFinite() || !atr15.isFinite() || atr5 <= 0.0 || atr15 <= 0.0) {
            return noTrade("ATR 无效；不生成进场计划。")
        }

        val last5 = m5.last()
        val prior5 = m5[m5.lastIndex - 1]
        val ema20m5 = analysis.m5.indicators.ema20 ?: return noTrade("M5 EMA20 不可用。")
        val swing = SwingSupportResistanceAnalyzer.analyze(
            candles = m15,
            leftBars = 3,
            rightBars = 3,
            currentPrice = price,
            atr = atr15
        )

        val h1Bull = directionalScore(analysis.h1, h1.last().close, true)
        val h1Bear = directionalScore(analysis.h1, h1.last().close, false)
        val m15Bull = directionalScore(analysis.m15, m15.last().close, true)
        val m15Bear = directionalScore(analysis.m15, m15.last().close, false)
        val m5Bull = directionalScore(analysis.m5, last5.close, true)
        val m5Bear = directionalScore(analysis.m5, last5.close, false)
        val buyScore = h1Bull + m15Bull + m5Bull
        val sellScore = h1Bear + m15Bear + m5Bear

        val bullishHigherTimeframes = h1Bull >= 4 && m15Bull >= 4 && h1Bull > h1Bear && m15Bull > m15Bear
        val bearishHigherTimeframes = h1Bear >= 4 && m15Bear >= 4 && h1Bear > h1Bull && m15Bear > m15Bull

        val priorHigh = m5.dropLast(1).takeLast(12).maxOfOrNull { it.high }
        val priorLow = m5.dropLast(1).takeLast(12).minOfOrNull { it.low }
        val bullishBreakout = priorHigh != null && last5.close > priorHigh && price >= priorHigh
        val bearishBreakout = priorLow != null && last5.close < priorLow && price <= priorLow
        val quoteGap = abs(price - last5.close)
        val distanceFromEma = abs(price - ema20m5)
        val notChasing = distanceFromEma <= atr5 * MAX_CHASE_ATR_M5 &&
            quoteGap <= atr5 * MAX_QUOTE_GAP_ATR_M5
        val bullishRejection = last5.isBullish &&
            last5.lowerWick >= max(last5.bodySize * 0.5, atr5 * 0.08) &&
            last5.close > prior5.close && price >= last5.close - atr5 * 0.20
        val bearishRejection = last5.isBearish &&
            last5.upperWick >= max(last5.bodySize * 0.5, atr5 * 0.08) &&
            last5.close < prior5.close && price <= last5.close + atr5 * 0.20
        val pullbackZone = distanceFromEma <= atr5 * 0.35

        val resistanceText = swing.nearestResistance?.let { fmt(it.low) + "–" + fmt(it.high) } ?: "暂无确认阻力区"
        val supportText = swing.nearestSupport?.let { fmt(it.low) + "–" + fmt(it.high) } ?: "暂无确认支撑区"
        val context = "参考价 ${fmt(price)}；H1 多空分 ${h1Bull}/${h1Bear}，M15 ${m15Bull}/${m15Bear}，M5 ${m5Bull}/${m5Bear}；支撑 $supportText，阻力 $resistanceText。"

        if (!notChasing) {
            val reason = if (distanceFromEma > atr5 * MAX_CHASE_ATR_M5) {
                "WAIT：价格距离 M5 EMA20 已超过 ${fmt(MAX_CHASE_ATR_M5)} ATR，存在追价风险。等待回踩或新结构形成。"
            } else {
                "WAIT：当前报价与最近闭合 M5 K 线相差过大，行情可能已跳动；刷新数据后再判断。"
            }
            return wait("$reason\n$context")
        }

        if (buyScore < config.minimumScore && sellScore < config.minimumScore) {
            return wait("WAIT：多空证据不足，暂不押注方向。\n$context")
        }

        if (bullishHigherTimeframes && buyScore >= config.minimumScore && buyScore > sellScore) {
            val trigger = bullishBreakout || (pullbackZone && bullishRejection)
            if (!trigger) {
                val level = priorHigh?.let(::fmt) ?: "待形成"
                return wait("偏多观察，不是进场信号：等待 M5 收盘突破 $level，或 EMA20 附近出现确认的多头拒绝 K 线。禁止仅因看涨就追单。\n$context")
            }
            val setup = createSetup(
                direction = TradeDirection.BUY, entry = price, atr = atr15,
                targetR = config.targetR, support = swing.nearestSupport,
                resistance = swing.nearestResistance, candles = m15
            ) ?: return wait("偏多结构成立，但止损/阻力空间不合格，取消 BUY。\n$context")
            val setupType = if (bullishBreakout) "M5 收盘突破近 12 根 K 线高点" else "EMA20 回踩后的多头拒绝"
            return DecisionResult(
                action = DecisionAction.BUY,
                setup = setup,
                confidence = (buyScore / 18.0).coerceIn(0.0, 1.0),
                reason = "BUY 条件成立：$setupType；H1 与 M15 同向，且 M5 触发确认。\n${formatSetup(setup, atr15)}\n$context\n评分是规则符合度，不是胜率概率。"
            )
        }

        if (bearishHigherTimeframes && sellScore >= config.minimumScore && sellScore > buyScore) {
            val trigger = bearishBreakout || (pullbackZone && bearishRejection)
            if (!trigger) {
                val level = priorLow?.let(::fmt) ?: "待形成"
                return wait("偏空观察，不是进场信号：等待 M5 收盘跌破 $level，或 EMA20 附近出现确认的空头拒绝 K 线。禁止仅因看跌就追单。\n$context")
            }
            val setup = createSetup(
                direction = TradeDirection.SELL, entry = price, atr = atr15,
                targetR = config.targetR, support = swing.nearestSupport,
                resistance = swing.nearestResistance, candles = m15
            ) ?: return wait("偏空结构成立，但止损/支撑空间不合格，取消 SELL。\n$context")
            val setupType = if (bearishBreakout) "M5 收盘跌破近 12 根 K 线低点" else "EMA20 回踩后的空头拒绝"
            return DecisionResult(
                action = DecisionAction.SELL,
                setup = setup,
                confidence = (sellScore / 18.0).coerceIn(0.0, 1.0),
                reason = "SELL 条件成立：$setupType；H1 与 M15 同向，且 M5 触发确认。\n${formatSetup(setup, atr15)}\n$context\n评分是规则符合度，不是胜率概率。"
            )
        }

        val conflict = !bullishHigherTimeframes && !bearishHigherTimeframes
        return wait(
            (if (conflict) "WAIT：H1 与 M15 没有形成一致方向，避免在大周期冲突时硬做单。" else "WAIT：多空评分接近，优势不足。") +
                "\n$context"
        )
    }

    private fun directionalScore(
        analysis: TimeframeAnalysis,
        close: Double,
        bullish: Boolean
    ): Int {
        var score = 0
        val indicators = analysis.indicators
        if (analysis.structure.trend == if (bullish) Trend.BULLISH else Trend.BEARISH) score++
        val ema20 = indicators.ema20
        val ema50 = indicators.ema50
        if (ema20 != null && ema50 != null && (if (bullish) ema20 > ema50 else ema20 < ema50)) score++
        if (ema20 != null && (if (bullish) close > ema20 else close < ema20)) score++
        val rsi = indicators.rsi14
        if (rsi != null && (if (bullish) rsi >= 52.0 else rsi <= 48.0)) score++
        val histogram = indicators.macdHistogram
        if (histogram != null && (if (bullish) histogram > 0.0 else histogram < 0.0)) score++
        val adx = indicators.adx14
        if (adx != null && adx >= 18.0) score++
        return score
    }

    private fun createSetup(
        direction: TradeDirection,
        entry: Double,
        atr: Double,
        targetR: Double,
        support: PriceZone?,
        resistance: PriceZone?,
        candles: List<Candle>
    ): TradeSetup? {
        val buffer = atr * 0.15
        val stop = when (direction) {
            TradeDirection.BUY -> {
                val structural = support?.low ?: candles.takeLast(12).minOf { it.low }
                min(structural - buffer, entry - atr * MIN_STOP_ATR)
            }
            TradeDirection.SELL -> {
                val structural = resistance?.high ?: candles.takeLast(12).maxOf { it.high }
                max(structural + buffer, entry + atr * MIN_STOP_ATR)
            }
        }
        val risk = abs(entry - stop)
        if (!risk.isFinite() || risk < atr * MIN_STOP_ATR || risk > atr * MAX_STOP_ATR) return null

        val target = when (direction) {
            TradeDirection.BUY -> {
                val structural = resistance?.low?.minus(buffer)
                if (structural != null && structural > entry) {
                    if ((structural - entry) / risk < MIN_NET_RR_BEFORE_COSTS) return null
                    min(entry + risk * targetR, structural)
                } else entry + risk * targetR
            }
            TradeDirection.SELL -> {
                val structural = support?.high?.plus(buffer)
                if (structural != null && structural < entry) {
                    if ((entry - structural) / risk < MIN_NET_RR_BEFORE_COSTS) return null
                    max(entry - risk * targetR, structural)
                } else entry - risk * targetR
            }
        }
        val rr = abs(target - entry) / risk
        if (!rr.isFinite() || rr < MIN_NET_RR_BEFORE_COSTS) return null
        val reason = if (direction == TradeDirection.BUY) "多周期偏多，M5 触发确认" else "多周期偏空，M5 触发确认"
        return TradeSetup(direction, entry, stop, target, rr, reason).takeIf { it.isValid() }
    }

    private fun formatSetup(setup: TradeSetup, atr: Double): String =
        "Entry ${fmt(setup.entry)} · SL ${fmt(setup.stopLoss)} · TP ${fmt(setup.takeProfit)} · R:R 1:${fmt(setup.riskReward)} · SL 距离 ${fmt(abs(setup.entry - setup.stopLoss))}（${fmt(abs(setup.entry - setup.stopLoss) / atr)} ATR）"

    private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun wait(reason: String) = DecisionResult(
        action = DecisionAction.WAIT, setup = null, confidence = 0.0, reason = reason
    )

    private fun noTrade(reason: String) = DecisionResult(
        action = DecisionAction.NO_TRADE, setup = null, confidence = 0.0, reason = reason
    )
}
