package com.goldbee.paper

import com.goldbee.decision.TradeDirection
import kotlin.math.abs

data class PaperQuote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestampMillis: Long
)

data class PaperSignal(
    val direction: TradeDirection,
    val plannedEntry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val source: String,
    val createdAtMillis: Long
)

enum class PaperTradeStatus {
    OPEN,
    CLOSED
}

enum class PaperExitReason {
    STOP_LOSS,
    TAKE_PROFIT,
    MANUAL
}

data class PaperTrade(
    val direction: TradeDirection,
    val entryPrice: Double,
    val plannedEntry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val entryBid: Double,
    val entryAsk: Double,
    val entryTimestampMillis: Long,
    val source: String,
    val lotSize: Double = 0.01,
    val contractSizeOunces: Double = 100.0,
    val commissionPerLotRoundTurnUsd: Double = 0.0,
    val status: PaperTradeStatus = PaperTradeStatus.OPEN,
    val exitPrice: Double? = null,
    val exitBid: Double? = null,
    val exitAsk: Double? = null,
    val exitTimestampMillis: Long? = null,
    val exitReason: PaperExitReason? = null,
    val pnlPrice: Double? = null,
    val pnlUsd: Double? = null
)

data class PaperTradeAttempt(
    val trade: PaperTrade?,
    val reason: String
) {
    val accepted: Boolean get() = trade != null
}

object PaperTradingEngine {
    const val MAX_QUOTE_AGE_MILLIS = 3_000L

    fun open(
        signal: PaperSignal,
        quote: PaperQuote,
        nowMillis: Long,
        maxEntryDistance: Double,
        minimumRiskReward: Double = 1.0,
        lotSize: Double = 0.01,
        contractSizeOunces: Double = 100.0,
        commissionPerLotRoundTurnUsd: Double = 0.0
    ): PaperTradeAttempt {
        val quoteError = validateQuote(quote, nowMillis)
        if (quoteError != null) return PaperTradeAttempt(null, quoteError)
        if (!signal.plannedEntry.isFinite() || signal.plannedEntry <= 0.0 ||
            !signal.stopLoss.isFinite() || signal.stopLoss <= 0.0 ||
            !signal.takeProfit.isFinite() || signal.takeProfit <= 0.0
        ) {
            return PaperTradeAttempt(null, "信号 Entry/SL/TP 无效，不能开模拟单。")
        }
        if (!maxEntryDistance.isFinite() || maxEntryDistance <= 0.0) {
            return PaperTradeAttempt(null, "无法验证允许的进场偏差；请重新分析市场。")
        }
        if (!minimumRiskReward.isFinite() || minimumRiskReward <= 0.0) {
            return PaperTradeAttempt(null, "模拟交易的最低风险回报设置无效。")
        }
        if (!lotSize.isFinite() || lotSize <= 0.0 ||
            !contractSizeOunces.isFinite() || contractSizeOunces <= 0.0 ||
            !commissionPerLotRoundTurnUsd.isFinite() || commissionPerLotRoundTurnUsd < 0.0
        ) {
            return PaperTradeAttempt(null, "模拟手数、每手合约规格或往返佣金无效。")
        }

        val entry = if (signal.direction == TradeDirection.BUY) quote.ask else quote.bid
        if (abs(entry - signal.plannedEntry) > maxEntryDistance) {
            return PaperTradeAttempt(null, "当前 MT5 报价已偏离信号 Entry，拒绝追价；请重新审核信号。")
        }
        val levelsValid = when (signal.direction) {
            TradeDirection.BUY -> signal.stopLoss < entry && signal.takeProfit > entry
            TradeDirection.SELL -> signal.stopLoss > entry && signal.takeProfit < entry
        }
        if (!levelsValid) {
            return PaperTradeAttempt(null, "按当前真实点差计算后，SL/TP 已不在有效方向，不能开模拟单。")
        }

        val risk = abs(entry - signal.stopLoss)
        val reward = abs(signal.takeProfit - entry)
        val rr = reward / risk
        if (!risk.isFinite() || !reward.isFinite() || risk <= 0.0 ||
            !rr.isFinite() || rr < minimumRiskReward
        ) {
            return PaperTradeAttempt(null, "按当前 MT5 Ask/Bid 计算，风险回报比低于最低要求。")
        }

        return PaperTradeAttempt(
            PaperTrade(
                direction = signal.direction,
                entryPrice = entry,
                plannedEntry = signal.plannedEntry,
                stopLoss = signal.stopLoss,
                takeProfit = signal.takeProfit,
                entryBid = quote.bid,
                entryAsk = quote.ask,
                entryTimestampMillis = quote.timestampMillis,
                source = signal.source,
                lotSize = lotSize,
                contractSizeOunces = contractSizeOunces,
                commissionPerLotRoundTurnUsd = commissionPerLotRoundTurnUsd
            ),
            "模拟单已开启；BUY 按 Ask 进场，SELL 按 Bid 进场。"
        )
    }

    fun update(
        trade: PaperTrade,
        quote: PaperQuote,
        nowMillis: Long
    ): PaperTradeAttempt {
        if (trade.status != PaperTradeStatus.OPEN) {
            return PaperTradeAttempt(null, "这笔模拟交易已经结束。")
        }
        val quoteError = validateQuote(quote, nowMillis)
        if (quoteError != null) return PaperTradeAttempt(null, quoteError)

        val exit: Pair<PaperExitReason, Double>? = when (trade.direction) {
            TradeDirection.BUY -> when {
                quote.bid <= trade.stopLoss -> PaperExitReason.STOP_LOSS to quote.bid
                quote.bid >= trade.takeProfit -> PaperExitReason.TAKE_PROFIT to trade.takeProfit
                else -> null
            }
            TradeDirection.SELL -> when {
                quote.ask >= trade.stopLoss -> PaperExitReason.STOP_LOSS to quote.ask
                quote.ask <= trade.takeProfit -> PaperExitReason.TAKE_PROFIT to trade.takeProfit
                else -> null
            }
        }

        if (exit == null) {
            return PaperTradeAttempt(
                trade.copy(),
                "模拟单仍持仓；按 MT5 平仓侧报价检查，尚未触及 SL/TP。"
            )
        }

        val (reason, exitPrice) = exit
        val pnl = when (trade.direction) {
            TradeDirection.BUY -> exitPrice - trade.entryPrice
            TradeDirection.SELL -> trade.entryPrice - exitPrice
        }
        return PaperTradeAttempt(
            trade.copy(
                status = PaperTradeStatus.CLOSED,
                exitPrice = exitPrice,
                exitBid = quote.bid,
                exitAsk = quote.ask,
                exitTimestampMillis = quote.timestampMillis,
                exitReason = reason,
                pnlPrice = pnl,
                pnlUsd = netPnlUsd(trade, pnl)
            ),
            "模拟单已平仓：$reason。"
        )
    }

    fun closeManually(
        trade: PaperTrade,
        quote: PaperQuote,
        nowMillis: Long
    ): PaperTradeAttempt {
        if (trade.status != PaperTradeStatus.OPEN) {
            return PaperTradeAttempt(null, "这笔模拟交易已经结束。")
        }
        val quoteError = validateQuote(quote, nowMillis)
        if (quoteError != null) return PaperTradeAttempt(null, quoteError)
        val exitPrice = if (trade.direction == TradeDirection.BUY) quote.bid else quote.ask
        val pnl = if (trade.direction == TradeDirection.BUY) {
            exitPrice - trade.entryPrice
        } else {
            trade.entryPrice - exitPrice
        }
        return PaperTradeAttempt(
            trade.copy(
                status = PaperTradeStatus.CLOSED,
                exitPrice = exitPrice,
                exitBid = quote.bid,
                exitAsk = quote.ask,
                exitTimestampMillis = quote.timestampMillis,
                exitReason = PaperExitReason.MANUAL,
                pnlPrice = pnl,
                pnlUsd = netPnlUsd(trade, pnl)
            ),
            "模拟单已按当前 MT5 平仓侧报价手动平仓。"
        )
    }

    private fun netPnlUsd(trade: PaperTrade, pricePnl: Double): Double =
        pricePnl * trade.contractSizeOunces * trade.lotSize -
            trade.commissionPerLotRoundTurnUsd * trade.lotSize

    private fun validateQuote(quote: PaperQuote, nowMillis: Long): String? {
        val symbol = quote.symbol.uppercase().replace("/", "").trim()
        if (symbol !in setOf("XAUUSD", "GOLD")) {
            return "当前屏幕品种不是已确认的 XAUUSD/GOLD，拒绝模拟成交。"
        }
        if (!quote.bid.isFinite() || !quote.ask.isFinite() ||
            quote.bid <= 0.0 || quote.ask <= quote.bid
        ) {
            return "MT5 Bid/Ask 无效，拒绝模拟成交。"
        }
        val age = nowMillis - quote.timestampMillis
        if (age !in 0L..MAX_QUOTE_AGE_MILLIS) {
            return "MT5 报价已过期或时间异常；请刷新报价后重试。"
        }
        return null
    }
}
