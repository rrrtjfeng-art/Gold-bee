package com.goldbee.risk

import kotlin.math.abs

/**
 * Final preflight for a user-confirmed real-account order.
 *
 * This is a local safety gate only. It does not connect to a broker or submit
 * an order. Values such as required margin and broker stop distance must come
 * from the actual MT5 account/symbol specification, not a generic estimate.
 */
data class RealAccountPreflightInput(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val quoteAgeMillis: Long,
    val accountBalance: Double,
    val accountEquity: Double,
    val freeMargin: Double,
    val brokerReportedRequiredMargin: Double,
    val lotSize: Double,
    val brokerMinimumLot: Double,
    val brokerMaximumLot: Double,
    val brokerLotStep: Double,
    val estimatedStopLossMoney: Double,
    val maximumRiskPercent: Double,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val brokerStopDistanceVerified: Boolean,
    val brokerMarginVerified: Boolean,
    val userExplicitlyConfirmed: Boolean
)

data class RealAccountPreflightResult(
    val ready: Boolean,
    val reason: String,
    val estimatedRiskPercent: Double?
)

object RealAccountPreflight {
    fun evaluate(input: RealAccountPreflightInput): RealAccountPreflightResult {
        fun reject(message: String) =
            RealAccountPreflightResult(false, message, null)

        val symbol = input.symbol.uppercase().replace("/", "").trim()
        if (symbol !in setOf("XAUUSD", "GOLD")) {
            return reject("拒绝：账户品种不是已确认的 XAUUSD/GOLD。")
        }
        if (!input.bid.isFinite() || !input.ask.isFinite() ||
            input.bid <= 0.0 || input.ask <= input.bid
        ) return reject("拒绝：Bid/Ask 无效。")
        if (input.quoteAgeMillis !in 0L..2_000L) {
            return reject("拒绝：报价超过 2 秒或报价时间无效；刷新 MT5 后重新检查。")
        }
        if (!input.accountBalance.isFinite() || input.accountBalance <= 0.0 ||
            !input.accountEquity.isFinite() || input.accountEquity <= 0.0 ||
            !input.freeMargin.isFinite() || input.freeMargin < 0.0
        ) return reject("拒绝：账户余额、净值或可用保证金无效。")
        if (!input.brokerMarginVerified ||
            !input.brokerReportedRequiredMargin.isFinite() ||
            input.brokerReportedRequiredMargin <= 0.0
        ) return reject("拒绝：必须先核对 MT5 实际所需保证金；不能用通用杠杆公式代替。")
        if (input.freeMargin < input.brokerReportedRequiredMargin) {
            return reject("拒绝：可用保证金低于经纪商报告的所需保证金。")
        }
        if (!input.brokerStopDistanceVerified) {
            return reject("拒绝：尚未核对该品种的经纪商最小挂单、止损及冻结距离。")
        }
        if (!input.lotSize.isFinite() || !input.brokerMinimumLot.isFinite() ||
            !input.brokerMaximumLot.isFinite() || !input.brokerLotStep.isFinite() ||
            input.brokerMinimumLot <= 0.0 ||
            input.brokerMaximumLot < input.brokerMinimumLot ||
            input.brokerLotStep <= 0.0 ||
            input.lotSize < input.brokerMinimumLot ||
            input.lotSize > input.brokerMaximumLot
        ) return reject("拒绝：手数不符合经纪商最小/最大手数范围。")
        val steps = (input.lotSize - input.brokerMinimumLot) / input.brokerLotStep
        if (!steps.isFinite() || abs(steps - steps.toLong().toDouble()) > 1e-6) {
            return reject("拒绝：手数不符合经纪商手数步进。")
        }
        if (!input.entry.isFinite() || !input.stopLoss.isFinite() ||
            !input.takeProfit.isFinite() || input.entry <= 0.0 ||
            input.stopLoss <= 0.0 || input.takeProfit <= 0.0
        ) return reject("拒绝：Entry、SL 或 TP 无效。")
        val stopCorrect = if (input.entry >= input.stopLoss) {
            input.stopLoss < input.entry && input.takeProfit > input.entry
        } else {
            input.stopLoss > input.entry && input.takeProfit < input.entry
        }
        if (!stopCorrect) return reject("拒绝：SL/TP 与交易方向或 Entry 不匹配。")
        if (!input.estimatedStopLossMoney.isFinite() ||
            input.estimatedStopLossMoney <= 0.0 ||
            !input.maximumRiskPercent.isFinite() ||
            input.maximumRiskPercent <= 0.0
        ) return reject("拒绝：止损金额或单笔风险上限无效。")
        val riskPercent = input.estimatedStopLossMoney / input.accountEquity * 100.0
        if (!riskPercent.isFinite() || riskPercent > input.maximumRiskPercent) {
            return RealAccountPreflightResult(
                false,
                "拒绝：预计止损占净值 %.2f%%，超过设定上限 %.2f%%。".format(
                    riskPercent, input.maximumRiskPercent
                ),
                riskPercent
            )
        }
        if (!input.userExplicitlyConfirmed) {
            return RealAccountPreflightResult(
                false,
                "风险检查通过，但尚未收到你的明确实盘确认；不会执行真实订单。",
                riskPercent
            )
        }
        return RealAccountPreflightResult(
            true,
            "实盘下单前检查通过。此结果不发送订单；请在 MT5 最后核对品种、方向、手数、SL/TP 和报价后自行提交。",
            riskPercent
        )
    }
}
