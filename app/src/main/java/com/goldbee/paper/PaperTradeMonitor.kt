package com.goldbee.paper

import android.content.Context
import org.json.JSONObject

/**
 * Checks an already user-confirmed local paper trade whenever MT5 quotes are observed.
 * This never interacts with MT5 controls and never submits an order.
 */
object PaperTradeMonitor {
    private const val SETTINGS_PREFS = "gold_bee_settings"
    private const val TRADE_KEY = "paper_trade_json"
    private const val STATUS_KEY = "paper_monitor_status"
    private const val CHECKED_AT_KEY = "paper_monitor_checked_at"

    @Synchronized
    fun checkAndUpdate(context: Context) {
        val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val rawTrade = settings.getString(TRADE_KEY, null) ?: return
        val trade = decodeTrade(rawTrade) ?: run {
            settings.edit().putString(STATUS_KEY, "模拟持仓记录无法读取；自动监控已暂停。").apply()
            return
        }
        if (trade.status != PaperTradeStatus.OPEN) return

        val observed = context.getSharedPreferences(
            com.goldbee.mt5.Mt5ScreenAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val source = observed.getString(
            com.goldbee.mt5.Mt5ScreenAccessibilityService.KEY_SOURCE, ""
        ).orEmpty()
        if (source != "ACCESSIBILITY" && source != "SCREEN_OCR") {
            setStatus(settings, "等待 MT5 只读报价；未取得有效报价时不会判断 SL/TP。")
            return
        }

        val quote = PaperQuote(
            symbol = observed.getString(
                com.goldbee.mt5.Mt5ScreenAccessibilityService.KEY_SYMBOL, ""
            ).orEmpty(),
            bid = observed.getString(
                com.goldbee.mt5.Mt5ScreenAccessibilityService.KEY_BID, ""
            ).orEmpty().toDoubleOrNull() ?: Double.NaN,
            ask = observed.getString(
                com.goldbee.mt5.Mt5ScreenAccessibilityService.KEY_ASK, ""
            ).orEmpty().toDoubleOrNull() ?: Double.NaN,
            timestampMillis = observed.getLong(
                com.goldbee.mt5.Mt5ScreenAccessibilityService.KEY_OBSERVED_AT, 0L
            )
        )
        val now = System.currentTimeMillis()
        val result = PaperTradingEngine.update(trade, quote, now)
        val updated = result.trade
        if (updated == null) {
            setStatus(settings, "${result.reason} 自动 SL/TP 检查暂停，等待有效的 MT5 报价。")
            return
        }

        if (updated.status == PaperTradeStatus.CLOSED) {
            saveTrade(settings, updated)
            recordClosure(settings, updated)
            setStatus(
                settings,
                "模拟单已自动平仓：${updated.exitReason} · 出场 ${updated.exitPrice} · 价格盈亏 ${updated.pnlPrice}。仅为模拟，不是账户货币金额。"
            )
        } else {
            setStatus(settings, "自动检查正常 · 最新 MT5 报价已核对 · 持仓仍未触及 SL/TP。")
        }
    }

    private fun setStatus(
        settings: android.content.SharedPreferences,
        message: String
    ) {
        settings.edit()
            .putString(STATUS_KEY, message)
            .putLong(CHECKED_AT_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun recordClosure(
        settings: android.content.SharedPreferences,
        trade: PaperTrade
    ) {
        val pnl = trade.pnlUsd ?: return
        val wins = settings.getInt("paper_wins", 0) + if (pnl > 0.0) 1 else 0
        val losses = settings.getInt("paper_losses", 0) + if (pnl < 0.0) 1 else 0
        val flats = settings.getInt("paper_flats", 0) + if (pnl == 0.0) 1 else 0
        val total = settings.getFloat("paper_total_pnl_usd", 0f).toDouble() + pnl
        settings.edit()
            .putInt("paper_wins", wins)
            .putInt("paper_losses", losses)
            .putInt("paper_flats", flats)
            .putFloat("paper_total_pnl_price", total.toFloat())
            .apply()
    }

    private fun saveTrade(
        settings: android.content.SharedPreferences,
        trade: PaperTrade
    ) {
        val json = JSONObject()
            .put("direction", trade.direction.name)
            .put("entryPrice", trade.entryPrice)
            .put("plannedEntry", trade.plannedEntry)
            .put("stopLoss", trade.stopLoss)
            .put("takeProfit", trade.takeProfit)
            .put("entryBid", trade.entryBid)
            .put("entryAsk", trade.entryAsk)
            .put("entryTimestampMillis", trade.entryTimestampMillis)
            .put("source", trade.source)
            .put("status", trade.status.name)
        fun nullable(key: String, value: Any?) {
            json.put(key, value ?: JSONObject.NULL)
        }
        nullable("exitPrice", trade.exitPrice)
        nullable("exitBid", trade.exitBid)
        nullable("exitAsk", trade.exitAsk)
        nullable("exitTimestampMillis", trade.exitTimestampMillis)
        nullable("exitReason", trade.exitReason?.name)
        nullable("pnlPrice", trade.pnlPrice)
        nullable("pnlUsd", trade.pnlUsd)
        settings.edit().putString(TRADE_KEY, json.toString()).apply()
    }

    private fun decodeTrade(raw: String): PaperTrade? = try {
        val json = JSONObject(raw)
        fun nullableDouble(key: String): Double? =
            if (json.isNull(key)) null else json.optDouble(key).takeIf { it.isFinite() }
        fun nullableLong(key: String): Long? =
            if (json.isNull(key)) null else json.optLong(key)
        PaperTrade(
            direction = com.goldbee.decision.TradeDirection.valueOf(json.getString("direction")),
            entryPrice = json.getDouble("entryPrice"),
            plannedEntry = json.getDouble("plannedEntry"),
            stopLoss = json.getDouble("stopLoss"),
            takeProfit = json.getDouble("takeProfit"),
            entryBid = json.getDouble("entryBid"),
            entryAsk = json.getDouble("entryAsk"),
            entryTimestampMillis = json.getLong("entryTimestampMillis"),
            source = json.optString("source", "UNKNOWN"),
            lotSize = json.optDouble("lotSize", 0.01),
            contractSizeOunces = json.optDouble("contractSizeOunces", 100.0),
            commissionPerLotRoundTurnUsd = json.optDouble("commissionPerLotRoundTurnUsd", 0.0),
            status = PaperTradeStatus.valueOf(json.getString("status")),
            exitPrice = nullableDouble("exitPrice"),
            exitBid = nullableDouble("exitBid"),
            exitAsk = nullableDouble("exitAsk"),
            exitTimestampMillis = nullableLong("exitTimestampMillis"),
            exitReason = if (json.isNull("exitReason")) null else PaperExitReason.valueOf(json.getString("exitReason")),
            pnlPrice = nullableDouble("pnlPrice"),
            pnlUsd = nullableDouble("pnlUsd")
        )
    } catch (_: Exception) {
        null
    }
}
