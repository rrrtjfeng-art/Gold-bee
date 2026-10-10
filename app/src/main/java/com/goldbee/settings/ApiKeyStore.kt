package com.goldbee.settings

import android.content.Context

/**
 * Backwards-compatible storage API for the Twelve Data key.
 * Secret material is encrypted with an Android Keystore-backed AES/GCM key.
 */
object ApiKeyStore {
    private const val KEY_API_KEY = "twelve_data_api_key"
    private const val LEGACY_PREF_NAME = "gold_bee_settings"

    fun save(context: Context, apiKey: String) {
        EncryptedApiKeyStore.save(context, KEY_API_KEY, apiKey)
        context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .apply()
    }

    fun get(context: Context): String {
        val secureValue = EncryptedApiKeyStore.get(context, KEY_API_KEY)
        if (secureValue.isNotBlank()) return secureValue

        // One-time migration from the older plaintext preference key.
        val legacyPrefs = context.getSharedPreferences(
            LEGACY_PREF_NAME,
            Context.MODE_PRIVATE
        )
        val legacyValue = legacyPrefs.getString(KEY_API_KEY, "").orEmpty()
        if (legacyValue.isBlank()) return ""

        EncryptedApiKeyStore.save(context, KEY_API_KEY, legacyValue)
        legacyPrefs.edit().remove(KEY_API_KEY).apply()
        return legacyValue
    }

    fun clear(context: Context) {
        EncryptedApiKeyStore.clear(context, KEY_API_KEY)
        context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .apply()
    }
}
