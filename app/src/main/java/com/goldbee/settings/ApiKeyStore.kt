package com.goldbee.settings

import android.content.Context

object ApiKeyStore {

    private const val PREF_NAME = "gold_bee_settings"
    private const val KEY_API_KEY = "twelve_data_api_key"

    fun save(
        context: Context,
        apiKey: String
    ) {
        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_API_KEY,
                apiKey.trim()
            )
            .apply()
    }

    fun get(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_API_KEY,
                ""
            )
            .orEmpty()
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
            .remove(KEY_API_KEY)
            .apply()
    }
}
