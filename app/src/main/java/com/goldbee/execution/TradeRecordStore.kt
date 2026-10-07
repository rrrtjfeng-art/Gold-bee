package com.goldbee.execution

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TradeRecordStore {

    private const val PREF_NAME = "gold_bee_trades"
    private const val KEY_RECORDS = "records"

    fun getAll(
        context: Context
    ): List<TradeRecord> {

        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val raw =
            preferences.getString(
                KEY_RECORDS,
                "[]"
            ) ?: "[]"

        return try {

            val array =
                JSONArray(raw)

            val result =
                mutableListOf<TradeRecord>()

            for (i in 0 until array.length()) {

                val record =
                    fromJson(
                        array.getJSONObject(i)
                    )

                if (record != null) {
                    result.add(record)
                }
            }

            result

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(
        context: Context,
        record: TradeRecord
    ) {

        if (!record.isValid()) {
            return
        }

        val records =
            getAll(context)
                .toMutableList()

        records.add(record)

        save(
            context,
            records
        )
    }

    fun update(
        context: Context,
        record: TradeRecord
    ) {

        if (!record.isValid()) {
            return
        }

        val records =
            getAll(context)
                .toMutableList()

        val index =
            records.indexOfFirst {
                it.id == record.id
            }

        if (index >= 0) {
            records[index] = record
        } else {
            records.add(record)
        }

        save(
            context,
            records
        )
    }

    fun clear(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_RECORDS)
            .apply()
    }

    private fun save(
        context: Context,
        records: List<TradeRecord>
    ) {

        val array =
            JSONArray()

        records.forEach { record ->
            array.put(
                toJson(record)
            )
        }

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_RECORDS,
                array.toString()
            )
            .apply()
    }

    private fun toJson(
        record: TradeRecord
    ): JSONObject {

        return JSONObject()
            .put("id", record.id)
            .put("symbol", record.symbol)
            .put(
                "direction",
                record.direction.name
            )
            .put(
                "entryPrice",
                record.entryPrice
            )
            .put(
                "stopLoss",
                record.stopLoss
            )
            .put(
                "takeProfit",
                record.takeProfit
            )
            .put(
                "volume",
                record.volume
            )
            .put(
                "result",
                record.result.name
            )
            .put(
                "profitLoss",
                record.profitLoss
            )
            .put(
                "openedAt",
                record.openedAt
            )
            .put(
                "closedAt",
                record.closedAt ?: JSONObject.NULL
            )
    }

    private fun fromJson(
        json: JSONObject
    ): TradeRecord? {

        return try {

            val closedAt =
                if (
                    json.isNull("closedAt")
                ) {
                    null
                } else {
                    json.getLong("closedAt")
                }

            TradeRecord(
                id =
                    json.getString("id"),

                symbol =
                    json.getString("symbol"),

                direction =
                    com.goldbee.decision.TradeDirection
                        .valueOf(
                            json.getString("direction")
                        ),

                entryPrice =
                    json.getDouble("entryPrice"),

                stopLoss =
                    json.getDouble("stopLoss"),

                takeProfit =
                    json.getDouble("takeProfit"),

                volume =
                    json.getDouble("volume"),

                result =
                    TradeResult.valueOf(
                        json.getString("result")
                    ),

                profitLoss =
                    json.getDouble("profitLoss"),

                openedAt =
                    json.getLong("openedAt"),

                closedAt =
                    closedAt
            )

        } catch (_: Exception) {
            null
        }
    }
}
