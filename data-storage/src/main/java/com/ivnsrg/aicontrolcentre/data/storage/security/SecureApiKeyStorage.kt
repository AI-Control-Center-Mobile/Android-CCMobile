package com.ivnsrg.aicontrolcentre.data.storage.security

import android.content.Context
import android.content.SharedPreferences
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ProviderApiKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStorage(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(createEncryptedPreferences(context))

    @Suppress("UNUSED_PARAMETER")
    internal constructor(prefs: SharedPreferences, testOnly: Boolean) : this(prefs)

    fun getProviderKeys(): List<ProviderApiKey> {
        migrateLegacyKeyIfNeeded()
        return ModelProvider.entries.mapNotNull { provider ->
            prefs.getString(provider.storageKey, null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { ProviderApiKey(provider = provider, key = it) }
        }
    }

    fun getApiKey(provider: ModelProvider): String? {
        migrateLegacyKeyIfNeeded()
        return prefs.getString(provider.storageKey, null)
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    fun saveApiKey(provider: ModelProvider, key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) return
        prefs.edit()
            .putString(provider.storageKey, normalized)
            .apply()
    }

    fun clearApiKey(provider: ModelProvider) {
        prefs.edit()
            .remove(provider.storageKey)
            .apply()
    }

    fun clearAllApiKeys() {
        val editor = prefs.edit()
        ModelProvider.entries.forEach { provider ->
            editor.remove(provider.storageKey)
        }
        editor
            .remove(KEY_API)
            .remove(KEY_API_LIST)
            .apply()
    }

    private fun migrateLegacyKeyIfNeeded() {
        if (prefs.contains(ModelProvider.OPEN_ROUTER.storageKey)) return

        val legacyKey = prefs.getString(KEY_API_LIST, null)
            ?.split(KEY_DELIMITER)
            ?.map(String::trim)
            ?.firstOrNull(String::isNotBlank)
            ?: prefs.getString(KEY_API, null)
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: return

        prefs.edit()
            .putString(ModelProvider.OPEN_ROUTER.storageKey, legacyKey)
            .remove(KEY_API)
            .remove(KEY_API_LIST)
            .apply()
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

private val ModelProvider.storageKey: String
    get() = "provider_api_key_$name"
