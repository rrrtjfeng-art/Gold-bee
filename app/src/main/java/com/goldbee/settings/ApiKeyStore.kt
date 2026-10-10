package com.goldbee.settings

import android.content.Context

/**
 * Backwards-compatible storage API for the Twelve Data key.
 * Secret material is encrypted with an Android Keystore-backed AES/GCM key.
 */
object ApiKeyStore {
    private const val KEY_API_KEY = "twelve_data_api_key"

    fun save(context: Context, apiKey: String) {
        EncryptedApiKeyStore.save(context, KEY_API_KEY, apiKey)
    }

    fun get(context: Context): String {
        return EncryptedApiKeyStore.get(context, KEY_API_KEY)
    }

    fun clear(context: Context) {
        EncryptedApiKeyStore.clear(context, KEY_API_KEY)
    }
}
