package com.goldbee.decision

import kotlin.math.abs

data class PendingOrderSafetyResult(
    val allowed: Boolean,
    val reason: String
)

/**
 * Final local sanity check for a pending-order candidate.
 * This does not submit orders and cannot verify broker-specific execution rules
 * unless the caller supplies the broker's minimum stop distance.
 */
object PendingOrderSafetyCheck {
    fun evaluate(
        plan: PendingOrderPlan,
        bid: Double,
        ask: Double,
        atrM5: Double,
        quoteAgeMillis: Long,
        brokerMinimumDistance: Double = 0.0,
        maximumSpreadAtrRatio: Double = 0.15
    ): PendingOrderSafetyResult {
        if (!plan.isValid()) return reject("条件单价格结构或盈亏比校验失败。")
        if (!bid.isFinite() || !ask.isFinite() || bid <= 0.0 || ask <= bid) {
            return reject("MT5 Bid/Ask 无效，拒绝条件单候选。")
        }
        if (!atrM5.isFinite() || atrM5 <= 0.0) return reject("M5 ATR 无效，无法核验点差。")
        if (quoteAgeMillis !in 0L..3_000L) return reject("MT5 报价超过 3 秒或时间无效，条件单候选已拒绝。")
        if (!brokerMinimumDistance.isFinite() || brokerMinimumDistance < 0.0) {
            return reject("经纪商最小挂单距离参数无效。")
        }
        if (!maximumSpreadAtrRatio.isFinite() || maximumSpreadAtrRatio <= 0.0) {
            return reject("点差限制参数无效。")
        }
        val spread = ask - bid
        if (spread > atrM5 * maximumSpreadAtrRatio) {
            return reject("当前点差占 M5 ATR 过高，等待点差收窄。")
        }

        val entryDistanceFromExecutableSide = when (plan.type) {
            PendingOrderType.BUY_LIMIT -> ask - plan.entry
            PendingOrderType.SELL_LIMIT -> plan.entry - bid
            PendingOrderType.BUY_STOP -> plan.entry - ask
            PendingOrderType.SELL_STOP -> bid - plan.entry
        }
        if (!entryDistanceFromExecutableSide.isFinite() ||
            entryDistanceFromExecutableSide <= 0.0
        ) return reject("挂单 Entry 已不在正确的 LIMIT/STOP 一侧；禁止把市价单误当挂单。")
        if (entryDistanceFromExecutableSide < brokerMinimumDistance) {
            return reject("Entry 距离当前可执行报价小于经纪商最小挂单距离。")
        }

        val stopDistance = abs(plan.entry - plan.stopLoss)
        val target1Distance = abs(plan.takeProfit1 - plan.entry)
        val target2Distance = abs(plan.takeProfit2 - plan.entry)
        if (minOf(stopDistance, target1Distance, target2Distance) < brokerMinimumDistance) {
            return reject("SL/TP 距离小于输入的经纪商最小止损距离。")
        }

        return PendingOrderSafetyResult(
            allowed = true,
            reason = "通过本地条件单检查：报价新鲜、点差可接受、挂单方向正确、价格距离符合已输入的最小距离。仍须在 MT5 核对交易时段、冻结距离和合约规则。"
        )
    }

    private fun reject(reason: String) = PendingOrderSafetyResult(false, reason)
}
