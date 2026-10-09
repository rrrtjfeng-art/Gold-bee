package com.goldbee.decision

import kotlin.math.abs
import kotlin.math.max

enum class TradeDirection {
BUY,
SELL
}

data class TradeSetup(
val direction: TradeDirection,
val entry: Double,
val stopLoss: Double,
val takeProfit: Double,
val riskReward: Double,
val reason: String
) {

fun isValid(): Boolean {
    // 禁止无效数字和无穷大
    if (!entry.isFinite() || entry <= 0.0) return false
    if (!stopLoss.isFinite() || stopLoss <= 0.0) return false
    if (!takeProfit.isFinite() || takeProfit <= 0.0) return false
    if (!riskReward.isFinite() || riskReward <= 0.0) return false

    val risk = abs(entry - stopLoss)
    val reward = abs(takeProfit - entry)

    if (risk <= 0.0 || reward <= 0.0) return false

    // 检查止损和止盈方向
    val directionValid = when (direction) {
        TradeDirection.BUY ->
            stopLoss < entry && takeProfit > entry

        TradeDirection.SELL ->
            stopLoss > entry && takeProfit < entry
    }

    if (!directionValid) return false

    // 验证声明的盈亏比与实际价格距离一致
    val actualRiskReward = reward / risk
    val tolerance = max(0.01, actualRiskReward * 0.01)

    if (abs(actualRiskReward - riskReward) > tolerance) {
        return false
    }

    return reason.isNotBlank()
}

}
