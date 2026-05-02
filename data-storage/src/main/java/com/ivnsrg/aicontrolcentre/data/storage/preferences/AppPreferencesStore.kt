package com.ivnsrg.aicontrolcentre.data.storage.preferences

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

class AppPreferencesStore(
    context: Context,
) {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(FILE_NAME) },
    )

    val preferences = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    suspend fun saveGroqRateLimitSnapshot(
        remainingRequests: String?,
        remainingTokens: String?,
        resetRequests: String?,
        resetTokens: String?,
        updatedAt: Long,
    ) {
        dataStore.edit { prefs ->
            writeNullableString(prefs, GROQ_REMAINING_REQUESTS, remainingRequests)
            writeNullableString(prefs, GROQ_REMAINING_TOKENS, remainingTokens)
            writeNullableString(prefs, GROQ_RESET_REQUESTS, resetRequests)
            writeNullableString(prefs, GROQ_RESET_TOKENS, resetTokens)
            prefs[GROQ_UPDATED_AT] = updatedAt
        }
    }

    suspend fun getGroqRateLimitSnapshot(): GroqRateLimitSnapshot? {
        val prefs = preferences.first()
        val updatedAt = prefs[GROQ_UPDATED_AT] ?: return null
        return GroqRateLimitSnapshot(
            remainingRequests = prefs[GROQ_REMAINING_REQUESTS],
            remainingTokens = prefs[GROQ_REMAINING_TOKENS],
            resetRequests = prefs[GROQ_RESET_REQUESTS],
            resetTokens = prefs[GROQ_RESET_TOKENS],
            updatedAt = updatedAt,
        )
    }

    companion object {
        private const val FILE_NAME = "app_preferences.preferences_pb"
        val LAST_PROJECT_ID = stringPreferencesKey("last_project_id")
        private val GROQ_REMAINING_REQUESTS = stringPreferencesKey("groq_remaining_requests")
        private val GROQ_REMAINING_TOKENS = stringPreferencesKey("groq_remaining_tokens")
        private val GROQ_RESET_REQUESTS = stringPreferencesKey("groq_reset_requests")
        private val GROQ_RESET_TOKENS = stringPreferencesKey("groq_reset_tokens")
        private val GROQ_UPDATED_AT = longPreferencesKey("groq_updated_at")
    }
}

data class GroqRateLimitSnapshot(
    val remainingRequests: String?,
    val remainingTokens: String?,
    val resetRequests: String?,
    val resetTokens: String?,
    val updatedAt: Long,
)

private fun writeNullableString(
    prefs: androidx.datastore.preferences.core.MutablePreferences,
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    value: String?,
) {
    if (value == null) {
        prefs.remove(key)
    } else {
        prefs[key] = value
    }
}
