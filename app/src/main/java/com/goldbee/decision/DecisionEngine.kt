package com.goldbee.decision

import com.goldbee.analysis.IndicatorSnapshot
import com.goldbee.analysis.MarketStructure
import com.goldbee.analysis.Trend
import com.goldbee.market.Candle
import kotlin.math.abs

object DecisionEngine {

private const val MIN_CANDLES = 50
private const val MIN_SCORE = 5
private const val MIN_RISK_ATR = 0.5
private const val MAX_RISK_ATR = 2.5
private const val MAX_EMA_DISTANCE_ATR = 1.2
private const val MAX_CANDLE_RANGE_ATR = 2.0
private const val TARGET_R = 2.0
private const val MIN_TARGET_R = 1.5

fun decide(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    structure: MarketStructure
): DecisionResult {

    if (candles.size < MIN_CANDLES) {
        return noTrade("K线数量不足，至少需要 $MIN_CANDLES 根。")
    }

    if (!indicators.hasEnoughData()) {
        return noTrade("指标数据不足，暂不交易。")
    }

    val price = candles.last().close
    val ema9 = indicators.ema9
        ?: return noTrade("EMA9 数据不足。")
    val ema20 = indicators.ema20
        ?: return noTrade("EMA20 数据不足。")
    val ema50 = indicators.ema50
        ?: return noTrade("EMA50 数据不足。")
    val rsi = indicators.rsi14
        ?: return noTrade("RSI 数据不足。")
    val macdHistogram = indicators.macdHistogram
        ?: return noTrade("MACD 数据不足。")
    val atr = indicators.atr14
        ?: return noTrade("ATR 数据不足。")

    if (!price.isFinite() || price <= 0.0 ||
        !atr.isFinite() || atr <= 0.0
    ) {
        return noTrade("价格或 ATR 无效。")
    }

    // 避免价格偏离均线过远后追价。
    if (abs(price - ema20) > atr * MAX_EMA_DISTANCE_ATR) {
        return wait("价格偏离 EMA20 过远，避免追价，等待回归或重新形成结构。")
    }

    // 避免在异常放大的 K 线之后直接进场。
    val lastCandle = candles.last()
    if (lastCandle.range > atr * MAX_CANDLE_RANGE_ATR) {
        return wait("最新 K 线波幅过大，等待波动稳定。")
    }

    var buyScore = 0
    var sellScore = 0

    if (ema9 > ema20) buyScore++ else if (ema9 < ema20) sellScore++
    if (ema20 > ema50) buyScore++ else if (ema20 < ema50) sellScore++
    if (rsi > 50.0) buyScore++ else if (rsi < 50.0) sellScore++
    if (macdHistogram > 0.0) buyScore++ else if (macdHistogram < 0.0) sellScore++

    if (structure.higherHigh && structure.higherLow) buyScore += 2
    if (structure.lowerHigh && structure.lowerLow) sellScore += 2

    when (structure.trend) {
        Trend.BULLISH -> buyScore++
        Trend.BEARISH -> sellScore++
        Trend.SIDEWAYS -> Unit
    }

    if (buyScore >= MIN_SCORE && buyScore > sellScore) {
        return buildBuy(price, atr, indicators, structure, buyScore)
    }

    if (sellScore >= MIN_SCORE && sellScore > buyScore) {
        return buildSell(price, atr, indicators, structure, sellScore)
    }

    return wait("多空条件不足或信号冲突，等待更清晰的市场结构。")
}

private fun buildBuy(
    entry: Double,
    atr: Double,
    indicators: IndicatorSnapshot,
    structure: MarketStructure,
    score: Int
): DecisionResult {
    val ema20 = indicators.ema20
        ?: return noTrade("EMA20 数据不足。")

    val support = structure.support
    if (support != null && (!support.isFinite() || support <= 0.0)) {
        return noTrade("支撑位数据无效。")
    }

    val stopLoss = if (support != null) {
        support - atr * 0.25
    } else {
        entry - atr * 1.5
    }

    val risk = entry - stopLoss

    if (!risk.isFinite() || risk < atr * MIN_RISK_ATR ||
        risk > atr * MAX_RISK_ATR
    ) {
        return wait("BUY 止损距离不合理，等待更合适的入场位置。")
    }

    val resistance = structure.resistance
    if (resistance != null) {
        if (!resistance.isFinite() || resistance <= 0.0) {
            return noTrade("阻力位数据无效。")
        }

        if (resistance > entry &&
            resistance - entry < risk * MIN_TARGET_R
        ) {
            return wait("上方阻力太近，潜在空间不足。")
        }
    }

    val plannedTarget = entry + risk * TARGET_R
    val takeProfit = if (resistance != null && resistance > entry) {
        minOf(plannedTarget, resistance - atr * 0.1)
    } else {
        plannedTarget
    }

    val actualRR = (takeProfit - entry) / risk

    if (!actualRR.isFinite() || actualRR < MIN_TARGET_R) {
        return wait("BUY 潜在盈亏比不足，暂不进场。")
    }

    val setup = TradeSetup(
        direction = TradeDirection.BUY,
        entry = entry,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        riskReward = actualRR,
        reason = "偏多条件满足；已检查追价距离、止损距离和上方阻力。"
    )

    if (!setup.isValid()) {
        return noTrade("BUY 交易方案校验失败。")
    }

    return DecisionResult(
        action = DecisionAction.BUY,
        setup = setup,
        confidence = (score / 7.0).coerceIn(0.0, 1.0),
        reason = "${setup.reason} 评分仅代表条件符合程度，不代表实际胜率。"
    )
}

private fun buildSell(
    entry: Double,
    atr: Double,
    indicators: IndicatorSnapshot,
    structure: MarketStructure,
    score: Int
): DecisionResult {
    val ema20 = indicators.ema20
        ?: return noTrade("EMA20 数据不足。")

    val resistance = structure.resistance
    if (resistance != null &&
        (!resistance.isFinite() || resistance <= 0.0)
    ) {
        return noTrade("阻力位数据无效。")
    }

    val stopLoss = if (resistance != null) {
        resistance + atr * 0.25
    } else {
        entry + atr * 1.5
    }

    val risk = stopLoss - entry

    if (!risk.isFinite() || risk < atr * MIN_RISK_ATR ||
        risk > atr * MAX_RISK_ATR
    ) {
        return wait("SELL 止损距离不合理，等待更合适的入场位置。")
    }

    val support = structure.support
    if (support != null) {
        if (!support.isFinite() || support <= 0.0) {
            return noTrade("支撑位数据无效。")
        }

        if (support < entry &&
            entry - support < risk * MIN_TARGET_R
        ) {
            return wait("下方支撑太近，潜在空间不足。")
        }
    }

    val plannedTarget = entry - risk * TARGET_R
    val takeProfit = if (support != null && support < entry) {
        maxOf(plannedTarget, support + atr * 0.1)
    } else {
        plannedTarget
    }

    val actualRR = (entry - takeProfit) / risk

    if (!actualRR.isFinite() || actualRR < MIN_TARGET_R) {
        return wait("SELL 潜在盈亏比不足，暂不进场。")
    }

    val setup = TradeSetup(
        direction = TradeDirection.SELL,
        entry = entry,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        riskReward = actualRR,
        reason = "偏空条件满足；已检查追价距离、止损距离和下方支撑。"
    )

    if (!setup.isValid()) {
        return noTrade("SELL 交易方案校验失败。")
    }

    return DecisionResult(
        action = DecisionAction.SELL,
        setup = setup,
        confidence = (score / 7.0).coerceIn(0.0, 1.0),
        reason = "${setup.reason} 评分仅代表条件符合程度，不代表实际胜率。"
    )
}

private fun wait(reason: String) = DecisionResult(
    action = DecisionAction.WAIT,
    setup = null,
    confidence = 0.0,
    reason = reason
)

private fun noTrade(reason: String) = DecisionResult(
    action = DecisionAction.NO_TRADE,
    setup = null,
    confidence = 0.0,
    reason = reason
)

}
