package com.ivnsrg.aicontrolcentre.feature.settings

import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ProviderApiKey
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaRepository
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSnapshot
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSource
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaStatus
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaValue
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
            val viewModel = SettingsViewModel(repository, FakeProviderQuotaRepository())

            viewModel.updateKeyDraft("sk-or-v1-test")
            viewModel.saveKey()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                listOf(ProviderApiKey(provider = ModelProvider.OPEN_ROUTER, key = "sk-or-v1-test")),
                state.providerKeys,
            )
            assertEquals("", state.keyDraft)
            assertEquals(SettingsSaveStatus.Saved, state.saveStatus)
            assertTrue(state.saveMessage?.contains("saved locally") == true)
            assertEquals(1, state.quotaSnapshots.size)
            assertEquals(42.0, state.quotaSnapshots.single().values.single().value.toDouble(), 0.0)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `clear all data resets state and invokes callback`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val repository = FakeSettingsRepository().apply {
                saveApiKey(ModelProvider.OPEN_ROUTER, "sk-or-v1-test")
            }
            val viewModel = SettingsViewModel(repository, FakeProviderQuotaRepository())
            var callbackInvoked = false

            advanceUntilIdle()
            viewModel.clearAllData { callbackInvoked = true }
            advanceUntilIdle()

            assertEquals(SettingsUiState(), viewModel.uiState.value)
            assertTrue(callbackInvoked)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeProviderQuotaRepository : ProviderQuotaRepository {
    private val snapshots = listOf(
        ProviderQuotaSnapshot(
            provider = ModelProvider.OPEN_ROUTER,
            status = ProviderQuotaStatus.AVAILABLE,
            headline = "OpenRouter free-tier limits",
            values = listOf(ProviderQuotaValue("Remaining", "42.0")),
            detail = "Key: Primary",
            updatedAt = 1L,
            source = ProviderQuotaSource.LIVE,
        ),
    )

    override suspend fun getQuotaSnapshots(): List<ProviderQuotaSnapshot> = snapshots

    override suspend fun refreshQuotaSnapshots(): List<ProviderQuotaSnapshot> = snapshots

    override suspend fun recordRateLimitSnapshot(
        provider: ModelProvider,
        remainingRequests: String?,
        remainingTokens: String?,
        resetRequests: String?,
        resetTokens: String?,
    ) = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    private val keys = linkedMapOf<ModelProvider, String>()

    override suspend fun getProviderKeys(): List<ProviderApiKey> =
        keys.map { (provider, key) -> ProviderApiKey(provider = provider, key = key) }

    override suspend fun getApiKey(provider: ModelProvider): String? = keys[provider]

    override suspend fun saveApiKey(provider: ModelProvider, key: String) {
        keys[provider] = key
    }

    override suspend fun clearApiKey(provider: ModelProvider) {
        keys.remove(provider)
    }

    override suspend fun clearAllApiKeys() {
        keys.clear()
    }

    override suspend fun clearAllLocalData() {
        keys.clear()
    }
}
