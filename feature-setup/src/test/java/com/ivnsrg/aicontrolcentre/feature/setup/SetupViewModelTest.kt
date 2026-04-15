package com.ivnsrg.aicontrolcentre.feature.setup

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
class SetupViewModelTest {

    @Test
    fun `save api key confirms persisted readback before success`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val repository = FakeSetupSettingsRepository()
            val viewModel = SetupViewModel(repository)

            viewModel.updateApiKey("sk-or-v1-setup")
            viewModel.saveApiKey()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(SetupSaveStatus.Saved, state.saveStatus)
            assertTrue(state.saveMessage?.contains("Ключ сохранён") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeSetupSettingsRepository : SettingsRepository {
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
