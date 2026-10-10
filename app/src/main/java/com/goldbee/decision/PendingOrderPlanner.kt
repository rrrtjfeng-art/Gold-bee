package com.goldbee.decision

import kotlin.math.abs
import kotlin.math.max

enum class PendingOrderType(val label: String) {
    BUY_LIMIT("BUY LIMIT"),
    SELL_LIMIT("SELL LIMIT"),
    BUY_STOP("BUY STOP"),
    SELL_STOP("SELL STOP")
}

data class PendingOrderPlan(
    val type: PendingOrderType,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val riskReward1: Double,
    val riskReward2: Double,
    val expiresAfterM5Bars: Int,
    val cancelIfPriceBeyond: Double,
    val rationale: String
) {
    fun isValid(): Boolean {
        if (listOf(entry, stopLoss, takeProfit1, takeProfit2, riskReward1, riskReward2, cancelIfPriceBeyond)
                .any { !it.isFinite() || it <= 0.0 }) return false
        val buy = type == PendingOrderType.BUY_LIMIT || type == PendingOrderType.BUY_STOP
        val limit = type == PendingOrderType.BUY_LIMIT || type == PendingOrderType.SELL_LIMIT
        if (buy && !(stopLoss < entry && takeProfit1 > entry && takeProfit2 >= takeProfit1)) return false
        if (!buy && !(stopLoss > entry && takeProfit1 < entry && takeProfit2 <= takeProfit1)) return false
        if (limit && buy && entry >= cancelIfPriceBeyond) return false
        if (limit && !buy && entry <= cancelIfPriceBeyond) return false
        if (!limit && buy && entry <= cancelIfPriceBeyond) return false
        if (!limit && !buy && entry >= cancelIfPriceBeyond) return false
        val risk = abs(entry - stopLoss)
        return risk > 0.0 &&
            abs(abs(takeProfit1 - entry) / risk - riskReward1) < 0.02 &&
            abs(abs(takeProfit2 - entry) / risk - riskReward2) < 0.02 &&
            expiresAfterM5Bars in 1..12 && rationale.isNotBlank()
    }
}

/**
 * Creates candidate pending orders only after the primary multi-timeframe
 * decision has selected a direction. These candidates do not bypass live
 * quote freshness, spread, broker stop-distance, risk-size, or user checks.
 */
object PendingOrderPlanner {
    fun plan(
        direction: TradeDirection,
        currentPrice: Double,
        atrM5: Double,
        structuralStop: Double,
        structuralTarget: Double,
        preferBreakout: Boolean,
        ema20Price: Double? = null,
        breakoutLevel: Double? = null,
        minimumRewardRisk: Double = 1.5
    ): PendingOrderPlan? {
        if (!currentPrice.isFinite() || currentPrice <= 0.0 ||
            !atrM5.isFinite() || atrM5 <= 0.0 ||
            !structuralStop.isFinite() || structuralStop <= 0.0 ||
            !structuralTarget.isFinite() || structuralTarget <= 0.0 ||
            !minimumRewardRisk.isFinite() || minimumRewardRisk < 1.2
        ) return null

        val buy = direction == TradeDirection.BUY
        val offset = max(atrM5 * 0.20, currentPrice * 0.00005)
        val type: PendingOrderType
        val entry: Double
        val rationale: String

        if (preferBreakout) {
            type = if (buy) PendingOrderType.BUY_STOP else PendingOrderType.SELL_STOP
            val level = breakoutLevel?.takeIf {
                it.isFinite() && it > 0.0 && if (buy) it > currentPrice else it < currentPrice
            }
            entry = if (buy) max(currentPrice + offset, (level ?: currentPrice) + offset)
                    else minOf(currentPrice - offset, (level ?: currentPrice) - offset)
            rationale = "突破/延续条件单：触发价依据可用的近端结构位与当前价偏移；超过 3 根 M5 K 线未触发，或价格反向越过取消边界，取消订单。"
        } else {
            type = if (buy) PendingOrderType.BUY_LIMIT else PendingOrderType.SELL_LIMIT
            val ema = ema20Price?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val isCorrectSide = if (buy) ema < currentPrice - offset * 0.25
                                else ema > currentPrice + offset * 0.25
            val distance = abs(currentPrice - ema)
            if (!isCorrectSide || distance > atrM5 * 0.80) return null
            entry = ema
            rationale = "回踩限价单以 M5 EMA20 作为候选价；EMA20 已不在合适回踩区或距离超过 0.8 ATR 时不生成。超过 3 根 M5 K 线未触发，取消订单。"
        }

        val stop = if (buy) minOf(structuralStop, entry - atrM5 * 0.65)
                   else maxOf(structuralStop, entry + atrM5 * 0.65)
        val risk = abs(entry - stop)
        if (!risk.isFinite() || risk <= 0.0 || risk > atrM5 * 3.0) return null

        val structuralReward = if (buy) structuralTarget - entry else entry - structuralTarget
        if (!structuralReward.isFinite() || structuralReward / risk < minimumRewardRisk) return null
        val target1Distance = risk * minimumRewardRisk
        val target2Distance = minOf(structuralReward, risk * 2.5)
        if (target2Distance < target1Distance) return null
        val sign = if (buy) 1.0 else -1.0
        val cancelLevel = when (type) {
            PendingOrderType.BUY_LIMIT -> currentPrice + atrM5 * 0.35
            PendingOrderType.SELL_LIMIT -> currentPrice - atrM5 * 0.35
            PendingOrderType.BUY_STOP -> currentPrice - atrM5 * 0.35
            PendingOrderType.SELL_STOP -> currentPrice + atrM5 * 0.35
        }
        val plan = PendingOrderPlan(
            type = type,
            entry = entry,
            stopLoss = stop,
            takeProfit1 = entry + sign * target1Distance,
            takeProfit2 = entry + sign * target2Distance,
            riskReward1 = target1Distance / risk,
            riskReward2 = target2Distance / risk,
            expiresAfterM5Bars = 3,
            cancelIfPriceBeyond = cancelLevel,
            rationale = rationale
        )
        return plan.takeIf { it.isValid() }
    }
}
