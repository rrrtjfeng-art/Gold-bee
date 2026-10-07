package com.goldbee.execution

import android.content.Context
import com.goldbee.risk.RiskStateStore

object ExecutionResultHandler {

    fun handleOpened(
        context: Context,
        request: Mt5OrderRequest,
        result: Mt5OrderResult
    ): TradeRecord? {

        if (!result.success) {
            return null
        }

        val record =
            TradeRecord(
                id =
                    result.orderId
                        ?: createLocalId(),

                symbol =
                    request.symbol,

                direction =
                    request.direction,

                entryPrice =
                    result.executedPrice
                        ?: request.entryPrice,

                stopLoss =
                    request.stopLoss,

                takeProfit =
                    request.takeProfit,

                volume =
                    result.executedVolume
                        ?: request.volume,

                result =
                    TradeResult.OPEN,

                profitLoss = 0.0,

                openedAt =
                    result.timestamp
            )

        TradeRecordStore.add(
            context,
            record
        )

        return record
    }

    fun handleClosed(
        context: Context,
        record: TradeRecord,
        profitLoss: Double,
        closedAt: Long =
            System.currentTimeMillis()
    ): TradeRecord {

        val result =
            when {
                profitLoss > 0.0 ->
                    TradeResult.WIN

                profitLoss < 0.0 ->
                    TradeResult.LOSS

                else ->
                    TradeResult.CANCELLED
            }

        val updated =
            record.copy(
                result = result,
                profitLoss = profitLoss,
                closedAt = closedAt
            )

        TradeRecordStore.update(
            context,
            updated
        )

        if (result == TradeResult.LOSS) {

            val lossPercent =
                calculateLossPercent(
                    record = record,
                    profitLoss = profitLoss
                )

            RiskStateStore.recordLoss(
                context,
                lossPercent
            )

        } else if (
            result == TradeResult.WIN
        ) {

            RiskStateStore.recordWin(
                context
            )
        }

        return updated
    }

    private fun calculateLossPercent(
        record: TradeRecord,
        profitLoss: Double
    ): Double {

        val riskAmount =
            kotlin.math.abs(
                profitLoss
            )

        if (riskAmount <= 0.0) {
            return 0.0
        }

        return 0.0
    }

    private fun createLocalId(): String {
        return "GB-" +
                System.currentTimeMillis()
    }
}
