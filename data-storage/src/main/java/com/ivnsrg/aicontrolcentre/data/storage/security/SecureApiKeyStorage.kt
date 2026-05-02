package com.ivnsrg.aicontrolcentre.data.storage.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStorage(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(createEncryptedPreferences(context))

    @Suppress("UNUSED_PARAMETER")
    internal constructor(prefs: SharedPreferences, testOnly: Boolean) : this(prefs)

    fun getApiKeys(): List<String> {
        migrateLegacyKeyIfNeeded()
        return prefs.getString(KEY_API_LIST, null)
            ?.split(KEY_DELIMITER)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    fun getPrimaryApiKey(): String? = getApiKeys().firstOrNull()

    fun getApiKey(): String? = getPrimaryApiKey()

    fun addApiKey(key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) return

        persistKeys(listOf(normalized) + getApiKeys().filterNot { it == normalized })
    }

    fun removeApiKey(key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) return
        persistKeys(getApiKeys().filterNot { it == normalized })
    }

    fun saveApiKey(key: String) = addApiKey(key)

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_API)
            .remove(KEY_API_LIST)
            .apply()
    }

    private fun persistKeys(keys: List<String>) {
        prefs.edit()
            .putString(KEY_API_LIST, keys.joinToString(KEY_DELIMITER))
            .remove(KEY_API)
            .apply()
    }

    private fun migrateLegacyKeyIfNeeded() {
        if (prefs.contains(KEY_API_LIST)) return

        val legacyKey = prefs.getString(KEY_API, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return

        persistKeys(listOf(legacyKey))
    }

    companion object {
        private const val FILE_NAME = "secure_api_keys"
        private const val KEY_API = "openrouter_api_key"
        private const val KEY_API_LIST = "openrouter_api_keys"
        private const val KEY_DELIMITER = "\n"

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
