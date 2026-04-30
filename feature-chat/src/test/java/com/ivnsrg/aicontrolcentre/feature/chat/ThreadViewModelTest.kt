package com.ivnsrg.aicontrolcentre.feature.chat

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    @Test
    fun `successful streaming send persists user immediately and assistant on completion`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val threadsRepository = RecordingThreadsRepository()
            val viewModel = ThreadViewModel(
                threadId = 1L,
                threadsRepository = threadsRepository,
                modelsRepository = StaticModelsRepository(),
                settingsRepository = PresentKeySettingsRepository(),
                chatRepository = SucceedingChatRepository(),
            )

            advanceUntilIdle()
            viewModel.updatePrompt("Hello")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), threadsRepository.messages.value.map { it.role })
            assertEquals("Hello", threadsRepository.messages.value[0].content)
            assertEquals("Answer", threadsRepository.messages.value[1].content)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed streaming keeps persisted user and no assistant`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val threadsRepository = RecordingThreadsRepository()
            val viewModel = ThreadViewModel(
                threadId = 1L,
                threadsRepository = threadsRepository,
                modelsRepository = StaticModelsRepository(),
                settingsRepository = PresentKeySettingsRepository(),
                chatRepository = FailingChatRepository(),
            )

            advanceUntilIdle()
            viewModel.updatePrompt("Hello")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertEquals(listOf(MessageRole.USER), threadsRepository.messages.value.map { it.role })
            assertEquals("Hello", threadsRepository.messages.value.single().content)
            assertNull(viewModel.uiState.value.streamingAssistant)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `streaming state is cleared after completed response`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val threadsRepository = RecordingThreadsRepository()
            val viewModel = ThreadViewModel(
                threadId = 1L,
                threadsRepository = threadsRepository,
                modelsRepository = StaticModelsRepository(),
                settingsRepository = PresentKeySettingsRepository(),
                chatRepository = SucceedingChatRepository(),
            )

            advanceUntilIdle()
            viewModel.updatePrompt("Hello")
            viewModel.sendMessage()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isStreamingResponse)
            assertNull(viewModel.uiState.value.streamingAssistant)
            assertEquals("", viewModel.uiState.value.promptDraft)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class SucceedingChatRepository : ChatRepository {
    override suspend fun sendMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft = AssistantMessageDraft(
        content = "Answer",
        provider = ModelProvider.OPEN_ROUTER,
        model = modelId,
        latencyMs = 100L,
        estimatedCost = 0.001,
    )

    override fun streamMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        emit(AssistantStreamEvent.Streaming(accumulatedContent = "Ans", isProcessing = false))
        emit(
            AssistantStreamEvent.Completed(
                AssistantMessageDraft(
                    content = "Answer",
                    provider = ModelProvider.OPEN_ROUTER,
                    model = modelId,
                    latencyMs = 100L,
                    estimatedCost = 0.001,
                ),
            ),
        )
    }
}

private class FailingChatRepository : ChatRepository {
    override suspend fun sendMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft {
        throw UiException(UiError.Network("offline"))
    }

    override fun streamMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        emit(AssistantStreamEvent.Streaming(accumulatedContent = "Ans", isProcessing = false))
        throw UiException(UiError.Network("offline"))
    }
}

private class StaticModelsRepository : ModelsRepository {
    private val models = listOf(
        ModelCatalogEntry(
            id = "model-a",
            provider = ModelProvider.OPEN_ROUTER,
            model = "model-a",
            label = "Model A",
            supportsChat = true,
            supportsCompare = true,
        ),
    )

    override suspend fun getCachedModels(): List<ModelCatalogEntry> = models

    override suspend fun refreshModels(forceRefresh: Boolean): List<ModelCatalogEntry> = models
}

private class PresentKeySettingsRepository : SettingsRepository {
    override suspend fun getApiKeys(): List<String> = listOf("key")
    override suspend fun getPrimaryApiKey(): String = "key"
    override suspend fun addApiKey(key: String) = Unit
    override suspend fun removeApiKey(key: String) = Unit
    override suspend fun getApiKey(): String = "key"
    override suspend fun saveApiKey(key: String) = Unit
    override suspend fun clearApiKey() = Unit
    override suspend fun clearAllLocalData() = Unit
}

private class RecordingThreadsRepository : ThreadsRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())

    override fun observeThreads(projectId: Long): Flow<List<Thread>> = MutableStateFlow(emptyList())
    override suspend fun createThread(projectId: Long, title: String?): Long = 1L
    override suspend fun deleteThread(threadId: Long) = Unit
    override fun observeMessages(threadId: Long): Flow<List<Message>> = messages

    override suspend fun insertUserMessage(threadId: Long, content: String, targetModel: String) {
        append(
            role = MessageRole.USER,
            content = content,
            model = targetModel,
            latencyMs = null,
            estimatedCost = null,
        )
    }

    override suspend fun insertAssistantMessage(
        threadId: Long,
        content: String,
        provider: ModelProvider,
        model: String,
        latencyMs: Long?,
        estimatedCost: Double?,
    ) {
        append(
            role = MessageRole.ASSISTANT,
            content = content,
            model = model,
            latencyMs = latencyMs,
            estimatedCost = estimatedCost,
        )
    }

    override suspend fun updateThreadMetadata(threadId: Long, updatedAt: Long) = Unit

    private fun append(
        role: MessageRole,
        content: String,
        model: String?,
        latencyMs: Long?,
        estimatedCost: Double?,
    ) {
        val nextId = messages.value.size + 1L
        messages.value = messages.value + Message(
            id = nextId,
            threadId = 1L,
            role = role,
            content = content,
            provider = if (role == MessageRole.ASSISTANT) ModelProvider.OPEN_ROUTER else null,
            model = model,
            latencyMs = latencyMs,
            estimatedCost = estimatedCost,
            createdAt = nextId,
        )
    }
}
