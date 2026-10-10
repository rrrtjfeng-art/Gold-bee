package com.goldbee.risk

import com.goldbee.decision.TradeSetup
import kotlin.math.abs

enum class EntryValidationStatus {
    VALID,
    TOO_FAR,
    INVALID
}

data class EntryValidationResult(
    val status: EntryValidationStatus,
    val distance: Double,
    val maxAllowedDistance: Double,
    val reason: String
)

object EntryPriceGuard {

    fun validate(
        currentPrice: Double,
        setup: TradeSetup,
        atr: Double,
        maxAtrDistance: Double = 0.35
    ): EntryValidationResult {
        if (!currentPrice.isFinite() || currentPrice <= 0.0) {
            return invalid("当前价格无效。")
        }

        if (!atr.isFinite() || atr <= 0.0 ||
            !maxAtrDistance.isFinite() || maxAtrDistance <= 0.0
        ) {
            return invalid("ATR 或允许的入场距离无效，无法判断追价风险。")
        }

        if (!setup.isValid()) {
            return invalid("交易方案无效。")
        }

        val distance = abs(currentPrice - setup.entry)
        val maxAllowedDistance = atr * maxAtrDistance

        if (!distance.isFinite() || !maxAllowedDistance.isFinite() ||
            maxAllowedDistance <= 0.0
        ) {
            return invalid("入场距离计算结果无效。")
        }

        if (distance > maxAllowedDistance) {
            return EntryValidationResult(
                status = EntryValidationStatus.TOO_FAR,
                distance = distance,
                maxAllowedDistance = maxAllowedDistance,
                reason = "当前价格已经偏离原计划入场价，禁止追价。"
            )
        }

        return EntryValidationResult(
            status = EntryValidationStatus.VALID,
            distance = distance,
            maxAllowedDistance = maxAllowedDistance,
            reason = "当前价格仍在允许的入场范围内。"
        )
    }

    private fun invalid(reason: String): EntryValidationResult =
        EntryValidationResult(
            status = EntryValidationStatus.INVALID,
            distance = 0.0,
            maxAllowedDistance = 0.0,
            reason = reason
        )
}
