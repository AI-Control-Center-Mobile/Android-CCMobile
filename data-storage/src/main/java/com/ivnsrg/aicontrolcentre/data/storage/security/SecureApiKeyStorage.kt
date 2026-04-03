package com.ivnsrg.aicontrolcentre.data.storage.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStorage(
    context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getApiKey(): String? = prefs.getString(KEY_API, null)

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API, key).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    companion object {
        private const val FILE_NAME = "secure_api_keys"
        private const val KEY_API = "openrouter_api_key"
    }
}
