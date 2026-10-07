package com.goldbee.execution

import com.goldbee.decision.DecisionAction
import com.goldbee.decision.DecisionResult

object Mt5OrderRequestFactory {

    fun create(
        symbol: String,
        confirmedRequest: ConfirmedOrderRequest,
        volume: Double
    ): Result<Mt5OrderRequest> {

        if (symbol.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "交易品种不能为空。"
                )
            )
        }

        if (volume <= 0.0) {
            return Result.failure(
                IllegalArgumentException(
                    "交易手数必须大于 0。"
                )
            )
        }

        val decision =
            confirmedRequest.decision

        val setup =
            decision.setup
                ?: return Result.failure(
                    IllegalStateException(
                        "确认请求中没有交易方案。"
                    )
                )

        val direction =
            when (decision.action) {

                DecisionAction.BUY ->
                    com.goldbee.decision.TradeDirection.BUY

                DecisionAction.SELL ->
                    com.goldbee.decision.TradeDirection.SELL

                else ->
                    return Result.failure(
                        IllegalStateException(
                            "只有 BUY / SELL 可以生成 MT5 请求。"
                        )
                    )
            }

        val request =
            Mt5OrderRequest(
                symbol = symbol,
                direction = direction,
                volume = volume,
                entryPrice = setup.entry,
                stopLoss = setup.stopLoss,
                takeProfit = setup.takeProfit,
                createdAt =
                    System.currentTimeMillis()
            )

        if (!request.isValid()) {
            return Result.failure(
                IllegalStateException(
                    "MT5 订单请求无效。"
                )
            )
        }

        return Result.success(request)
    }
}
