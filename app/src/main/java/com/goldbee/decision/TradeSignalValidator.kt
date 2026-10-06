package com.goldbee.decision

import com.goldbee.analysis.MultiTimeframeAnalysis

data class SignalValidation(
    val valid: Boolean,
    val reason: String
)

object TradeSignalValidator {

    fun validate(
        direction: TradeDirection,
        analysis: MultiTimeframeAnalysis
    ): SignalValidation {

        val m5 = analysis.m5
        val m15 = analysis.m15
        val h1 = analysis.h1

        if (
            !m5.available ||
            !m15.available ||
            !h1.available
        ) {
            return SignalValidation(
                valid = false,
                reason = "M5、M15、H1 数据不足。"
            )
        }

        if (
            direction == TradeDirection.BUY &&
            !analysis.bullishAgreement()
        ) {
            return SignalValidation(
                valid = false,
                reason = "多周期没有形成足够的 BUY 共识。"
            )
        }

        if (
            direction == TradeDirection.SELL &&
            !analysis.bearishAgreement()
        ) {
            return SignalValidation(
                valid = false,
                reason = "多周期没有形成足够的 SELL 共识。"
            )
        }

        if (
            direction == TradeDirection.BUY &&
            h1.structure.trend == com.goldbee.analysis.Trend.BEARISH
        ) {
            return SignalValidation(
                valid = false,
                reason = "H1 仍然偏空，禁止直接 BUY。"
            )
        }

        if (
            direction == TradeDirection.SELL &&
            h1.structure.trend == com.goldbee.analysis.Trend.BULLISH
        ) {
            return SignalValidation(
                valid = false,
                reason = "H1 仍然偏多，禁止直接 SELL。"
            )
        }

        return SignalValidation(
            valid = true,
            reason = "M5、M15、H1 多周期方向一致。"
        )
    }
}
