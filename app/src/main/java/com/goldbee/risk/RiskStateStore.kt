package com.goldbee.risk

import android.content.Context
import java.time.LocalDate

object RiskStateStore {

    private const val PREF_NAME = "gold_bee_risk"

    private const val KEY_DATE = "risk_date"
    private const val KEY_DAILY_LOSS = "daily_loss_percent"
    private const val KEY_CONSECUTIVE_LOSSES =
        "consecutive_losses"

    fun get(
        context: Context
    ): RiskState {

        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val today =
            LocalDate.now().toString()

        val savedDate =
            preferences.getString(
                KEY_DATE,
                today
            )

        if (savedDate != today) {

            resetForNewDay(
                context
            )

            return RiskState()
        }

        return RiskState(
            dailyLossPercent =
                preferences.getFloat(
                    KEY_DAILY_LOSS,
                    0.0f
                ).toDouble(),

            consecutiveLosses =
                preferences.getInt(
                    KEY_CONSECUTIVE_LOSSES,
                    0
                )
        )
    }

    fun save(
        context: Context,
        state: RiskState
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_DATE,
                LocalDate.now().toString()
            )
            .putFloat(
                KEY_DAILY_LOSS,
                state.dailyLossPercent.toFloat()
            )
            .putInt(
                KEY_CONSECUTIVE_LOSSES,
                state.consecutiveLosses
            )
            .apply()
    }

    fun recordLoss(
        context: Context,
        lossPercent: Double
    ) {

        if (lossPercent <= 0.0) {
            return
        }

        val current =
            get(context)

        save(
            context,
            RiskState(
                dailyLossPercent =
                    current.dailyLossPercent +
                            lossPercent,

                consecutiveLosses =
                    current.consecutiveLosses + 1
            )
        )
    }

    fun recordWin(
        context: Context
    ) {

        val current =
            get(context)

        save(
            context,
            RiskState(
                dailyLossPercent =
                    current.dailyLossPercent,

                consecutiveLosses = 0
            )
        )
    }

    fun resetForNewDay(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_DATE,
                LocalDate.now().toString()
            )
            .putFloat(
                KEY_DAILY_LOSS,
                0.0f
            )
            .putInt(
                KEY_CONSECUTIVE_LOSSES,
                0
            )
            .apply()
    }
}
