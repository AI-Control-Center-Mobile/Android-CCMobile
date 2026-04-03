package com.ivnsrg.aicontrolcentre.data.storage.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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

    companion object {
        private const val FILE_NAME = "app_preferences.preferences_pb"
        val LAST_PROJECT_ID = stringPreferencesKey("last_project_id")
    }
}
