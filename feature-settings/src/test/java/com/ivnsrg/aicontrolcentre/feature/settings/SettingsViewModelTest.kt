package com.ivnsrg.aicontrolcentre.feature.settings

import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun `add key updates ui only after persisted readback`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val repository = FakeSettingsRepository()
            val viewModel = SettingsViewModel(repository)

            viewModel.updateKeyDraft("sk-or-v1-test")
            viewModel.addKey()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("sk-or-v1-test"), state.apiKeys)
            assertEquals("", state.keyDraft)
            assertEquals(SettingsSaveStatus.Saved, state.saveStatus)
            assertTrue(state.saveMessage?.contains("Ключ сохранён") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val keys = mutableListOf<String>()

    override suspend fun getApiKeys(): List<String> = keys.toList()

    override suspend fun getPrimaryApiKey(): String? = keys.firstOrNull()

    override suspend fun addApiKey(key: String) {
        keys.remove(key)
        keys.add(0, key)
    }

    override suspend fun removeApiKey(key: String) {
        keys.remove(key)
    }

    override suspend fun getApiKey(): String? = getPrimaryApiKey()

    override suspend fun saveApiKey(key: String) {
        addApiKey(key)
    }

    override suspend fun clearApiKey() {
        keys.clear()
    }

    override suspend fun clearAllLocalData() {
        keys.clear()
    }
}
