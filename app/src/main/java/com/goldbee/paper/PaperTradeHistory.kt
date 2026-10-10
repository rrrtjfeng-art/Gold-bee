package com.goldbee.paper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

data class PaperTradeHistoryRecord(
    val id: String,
    val direction: String,
    val source: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val entryTimestampMillis: Long,
    val exitTimestampMillis: Long,
    val exitReason: String,
    val lotSize: Double,
    val contractSizeOunces: Double,
    val commissionPerLotRoundTurnUsd: Double,
    val entrySpread: Double,
    val exitSpread: Double,
    val pnlPrice: Double,
    val pnlUsd: Double
) {
    companion object {
        fun fromTrade(trade: PaperTrade): PaperTradeHistoryRecord? {
            if (trade.status != PaperTradeStatus.CLOSED) return null
            val exitPrice = trade.exitPrice ?: return null
            val exitTime = trade.exitTimestampMillis ?: return null
            val pnlPrice = trade.pnlPrice ?: return null
            val pnlUsd = trade.pnlUsd ?: return null
            return PaperTradeHistoryRecord(
                id = "${trade.entryTimestampMillis}_${trade.direction.name}_${trade.source}",
                direction = trade.direction.name,
                source = trade.source,
                entryPrice = trade.entryPrice,
                exitPrice = exitPrice,
                stopLoss = trade.stopLoss,
                takeProfit = trade.takeProfit,
                entryTimestampMillis = trade.entryTimestampMillis,
                exitTimestampMillis = exitTime,
                exitReason = trade.exitReason?.name ?: "UNKNOWN",
                lotSize = trade.lotSize,
                contractSizeOunces = trade.contractSizeOunces,
                commissionPerLotRoundTurnUsd = trade.commissionPerLotRoundTurnUsd,
                entrySpread = trade.entryAsk - trade.entryBid,
                exitSpread = (trade.exitAsk ?: 0.0) - (trade.exitBid ?: 0.0),
                pnlPrice = pnlPrice,
                pnlUsd = pnlUsd
            )
        }
    }
}

data class PaperTradePerformanceSummary(
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val flats: Int,
    val winRatePercent: Double,
    val totalNetUsd: Double,
    val grossProfitUsd: Double,
    val grossLossUsd: Double,
    val profitFactor: Double?,
    val maxDrawdownUsd: Double,
    val endingBalanceUsd: Double
)

object PaperTradePerformance {
    fun summarize(
        records: List<PaperTradeHistoryRecord>,
        initialBalanceUsd: Double
    ): PaperTradePerformanceSummary {
        val ordered = records.sortedBy { it.exitTimestampMillis }
        val wins = ordered.count { it.pnlUsd > 0.0 }
        val losses = ordered.count { it.pnlUsd < 0.0 }
        val flats = ordered.count { it.pnlUsd == 0.0 }
        val grossProfit = ordered.filter { it.pnlUsd > 0.0 }.sumOf { it.pnlUsd }
        val grossLoss = -ordered.filter { it.pnlUsd < 0.0 }.sumOf { it.pnlUsd }
        val total = ordered.sumOf { it.pnlUsd }
        var equity = initialBalanceUsd
        var peak = initialBalanceUsd
        var maxDrawdown = 0.0
        ordered.forEach { record ->
            equity += record.pnlUsd
            peak = max(peak, equity)
            maxDrawdown = max(maxDrawdown, peak - equity)
        }
        return PaperTradePerformanceSummary(
            trades = ordered.size,
            wins = wins,
            losses = losses,
            flats = flats,
            winRatePercent = if (ordered.isEmpty()) 0.0 else wins * 100.0 / ordered.size,
            totalNetUsd = total,
            grossProfitUsd = grossProfit,
            grossLossUsd = grossLoss,
            profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else null,
            maxDrawdownUsd = maxDrawdown,
            endingBalanceUsd = initialBalanceUsd + total
        )
    }
}

object PaperTradeHistoryStore {
    private const val PREFS_NAME = "gold_bee_settings"
    private const val HISTORY_KEY = "paper_trade_history_json"
    private const val MAX_RECORDS = 500
    private val lock = Any()

    fun load(context: Context): List<PaperTradeHistoryRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(HISTORY_KEY, null) ?: return emptyList()
        return decode(raw)
    }

    fun append(context: Context, trade: PaperTrade) {
        val record = PaperTradeHistoryRecord.fromTrade(trade) ?: return
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = decode(prefs.getString(HISTORY_KEY, null).orEmpty())
            if (current.any { it.id == record.id }) return
            val updated = (current + record)
                .sortedBy { it.exitTimestampMillis }
                .takeLast(MAX_RECORDS)
            prefs.edit().putString(HISTORY_KEY, encode(updated)).apply()
        }
    }

    private fun encode(records: List<PaperTradeHistoryRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("direction", record.direction)
                    .put("source", record.source)
                    .put("entryPrice", record.entryPrice)
                    .put("exitPrice", record.exitPrice)
                    .put("stopLoss", record.stopLoss)
                    .put("takeProfit", record.takeProfit)
                    .put("entryTimestampMillis", record.entryTimestampMillis)
                    .put("exitTimestampMillis", record.exitTimestampMillis)
                    .put("exitReason", record.exitReason)
                    .put("lotSize", record.lotSize)
                    .put("contractSizeOunces", record.contractSizeOunces)
                    .put("commissionPerLotRoundTurnUsd", record.commissionPerLotRoundTurnUsd)
                    .put("entrySpread", record.entrySpread)
                    .put("exitSpread", record.exitSpread)
                    .put("pnlPrice", record.pnlPrice)
                    .put("pnlUsd", record.pnlUsd)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<PaperTradeHistoryRecord> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val record = runCatching {
                        PaperTradeHistoryRecord(
                            id = item.getString("id"),
                            direction = item.getString("direction"),
                            source = item.optString("source", "UNKNOWN"),
                            entryPrice = item.getDouble("entryPrice"),
                            exitPrice = item.getDouble("exitPrice"),
                            stopLoss = item.getDouble("stopLoss"),
                            takeProfit = item.getDouble("takeProfit"),
                            entryTimestampMillis = item.getLong("entryTimestampMillis"),
                            exitTimestampMillis = item.getLong("exitTimestampMillis"),
                            exitReason = item.optString("exitReason", "UNKNOWN"),
                            lotSize = item.optDouble("lotSize", 0.01),
                            contractSizeOunces = item.optDouble("contractSizeOunces", 100.0),
                            commissionPerLotRoundTurnUsd = item.optDouble("commissionPerLotRoundTurnUsd", 0.0),
                            entrySpread = item.optDouble("entrySpread", 0.0),
                            exitSpread = item.optDouble("exitSpread", 0.0),
                            pnlPrice = item.getDouble("pnlPrice"),
                            pnlUsd = item.getDouble("pnlUsd")
                        )
                    }.getOrNull()
                    if (record != null) add(record)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
