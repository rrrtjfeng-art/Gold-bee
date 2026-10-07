package com.goldbee.execution

import com.goldbee.decision.TradeDirection
import kotlin.math.abs

data class ExecutionPriceCheck(
    val allowed: Boolean,
    val currentPrice: Double,
    val requestedPrice: Double,
    val distance: Double,
    val reason: String
)

object ExecutionPriceGuard {

    fun check(
        request: Mt5OrderRequest,
        currentPrice: Double,
        maxDistance: Double
    ): ExecutionPriceCheck {

        if (currentPrice <= 0.0) {
            return blocked(
                request = request,
                reason = "执行前当前价格无效。"
            )
        }

        if (maxDistance <= 0.0) {
            return blocked(
                request = request,
                reason = "执行价格允许偏移必须大于 0。"
            )
        }

        val distance =
            abs(
                currentPrice -
                        request.entryPrice
            )

        if (distance > maxDistance) {
            return ExecutionPriceCheck(
                allowed = false,
                currentPrice = currentPrice,
                requestedPrice =
                    request.entryPrice,
                distance = distance,
                reason =
                    "执行前价格已经偏离原计划入场价，取消执行。"
            )
        }

        return ExecutionPriceCheck(
            allowed = true,
            currentPrice = currentPrice,
            requestedPrice =
                request.entryPrice,
            distance = distance,
            reason =
                "执行前价格仍在允许范围内。"
        )
    }

    private fun blocked(
        request: Mt5OrderRequest,
        reason: String
    ): ExecutionPriceCheck {

        return ExecutionPriceCheck(
            allowed = false,
            currentPrice = 0.0,
            requestedPrice =
                request.entryPrice,
            distance = 0.0,
            reason = reason
        )
    }
}
