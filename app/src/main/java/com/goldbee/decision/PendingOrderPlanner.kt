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
 * Builds a pending-order alternative from a directionally confirmed setup.
 * It does not establish direction by itself and must not bypass the main
 * decision gate, live quote checks, broker stop-distance rules, or user confirmation.
 */
object PendingOrderPlanner {
    fun plan(
        direction: TradeDirection,
        currentPrice: Double,
        atrM5: Double,
        structuralStop: Double,
        structuralTarget: Double,
        preferBreakout: Boolean,
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
        val entry = when {
            buy && preferBreakout -> currentPrice + offset
            buy -> currentPrice - offset
            !buy && preferBreakout -> currentPrice - offset
            else -> currentPrice + offset
        }
        val type = when {
            buy && preferBreakout -> PendingOrderType.BUY_STOP
            buy -> PendingOrderType.BUY_LIMIT
            !buy && preferBreakout -> PendingOrderType.SELL_STOP
            else -> PendingOrderType.SELL_LIMIT
        }

        val stop = if (buy) minOf(structuralStop, entry - atrM5 * 0.65)
                   else maxOf(structuralStop, entry + atrM5 * 0.65)
        val risk = abs(entry - stop)
        if (!risk.isFinite() || risk <= 0.0 || risk > atrM5 * 3.0) return null

        val structuralReward = if (buy) structuralTarget - entry else entry - structuralTarget
        if (structuralReward / risk < minimumRewardRisk) return null
        val target1Distance = max(risk * minimumRewardRisk, minOf(structuralReward, risk * 1.5))
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
            rationale = if (preferBreakout) {
                "条件单只在价格突破触发价时成交；若突破失败或超过 3 根 M5 K 线未触发，取消订单。"
            } else {
                "回踩限价单等待价格回到计划区；若行情越过取消边界或超过 3 根 M5 K 线未触发，取消订单。"
            }
        )
        return plan.takeIf { it.isValid() }
    }
}
