package com.ivnsrg.aicontrolcentre.feature.compare

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareResultPayload
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModelTest {

    @Test
    fun `winner for latest prompt does not duplicate user message`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val threadsRepository = RecordingCompareThreadsRepository(
                initialMessages = listOf(userMessage(id = 1L, content = "Compare this")),
            )
            val viewModel = CompareViewModel(
                threadId = 1L,
                initialSelectedModelId = "model-a",
                threadsRepository = threadsRepository,
                modelsRepository = StaticCompareModelsRepository(),
                settingsRepository = PresentCompareKeySettingsRepository(),
                compareRepository = StreamingCompareRepository(),
            )

            advanceUntilIdle()
            viewModel.compare()
            advanceUntilIdle()
            viewModel.continueWithWinner("model-a") {}
            advanceUntilIdle()

            assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), threadsRepository.messages.value.map { it.role })
            assertEquals("Compare this", threadsRepository.messages.value.single { it.role == MessageRole.USER }.content)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `winner for edited prompt persists one user and one assistant message`() = runTest {
        Dispatchers.setMain(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        try {
            val threadsRepository = RecordingCompareThreadsRepository(
                initialMessages = listOf(userMessage(id = 1L, content = "Original prompt")),
            )
            val viewModel = CompareViewModel(
                threadId = 1L,
                initialSelectedModelId = "model-a",
                threadsRepository = threadsRepository,
                modelsRepository = StaticCompareModelsRepository(),
                settingsRepository = PresentCompareKeySettingsRepository(),
                compareRepository = StreamingCompareRepository(),
            )

            advanceUntilIdle()
            viewModel.updatePrompt("Edited prompt")
            viewModel.compare()
            advanceUntilIdle()
            viewModel.continueWithWinner("model-a") {}
            advanceUntilIdle()

            assertEquals(
                listOf(MessageRole.USER, MessageRole.USER, MessageRole.ASSISTANT),
                threadsRepository.messages.value.map { it.role },
            )
            assertEquals("Edited prompt", threadsRepository.messages.value[1].content)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class StreamingCompareRepository : CompareRepository {
    override suspend fun compare(
        threadId: Long,
        modelA: String,
        modelB: String,
        prompt: String,
        history: List<Message>,
    ): CompareResultPayload = CompareResultPayload(
        first = draft(modelA),
        second = draft(modelB),
    )

    override fun streamModelResponse(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flowOf(
        AssistantStreamEvent.Completed(draft(modelId)),
    )

    private fun draft(modelId: String) = AssistantMessageDraft(
        content = "Answer from $modelId",
        provider = ModelProvider.OPEN_ROUTER,
        model = modelId,
        latencyMs = 100L,
        estimatedCost = 0.001,
    )
}

private class StaticCompareModelsRepository : ModelsRepository {
    private val models = listOf(
        model(id = "model-a"),
        model(id = "model-b"),
    )

    override suspend fun getCachedModels(): List<ModelCatalogEntry> = models

    override suspend fun refreshModels(forceRefresh: Boolean): List<ModelCatalogEntry> = models
}

private class PresentCompareKeySettingsRepository : SettingsRepository {
    override suspend fun getApiKeys(): List<String> = listOf("key")
    override suspend fun getPrimaryApiKey(): String = "key"
    override suspend fun addApiKey(key: String) = Unit
    override suspend fun removeApiKey(key: String) = Unit
    override suspend fun getApiKey(): String = "key"
    override suspend fun saveApiKey(key: String) = Unit
    override suspend fun clearApiKey() = Unit
    override suspend fun clearAllLocalData() = Unit
}

private class RecordingCompareThreadsRepository(
    initialMessages: List<Message>,
) : ThreadsRepository {
    val messages = MutableStateFlow(initialMessages)

    override fun observeThreads(projectId: Long): Flow<List<Thread>> = MutableStateFlow(emptyList())
    override suspend fun createThread(projectId: Long, title: String?): Long = 1L
    override suspend fun deleteThread(threadId: Long) = Unit
    override fun observeMessages(threadId: Long): Flow<List<Message>> = messages

    override suspend fun insertUserMessage(threadId: Long, content: String, targetModel: String) {
        append(role = MessageRole.USER, content = content, model = targetModel)
    }

    override suspend fun insertAssistantMessage(
        threadId: Long,
        content: String,
        provider: ModelProvider,
        model: String,
        latencyMs: Long?,
        estimatedCost: Double?,
    ) {
        append(role = MessageRole.ASSISTANT, content = content, model = model)
    }

    override suspend fun updateThreadMetadata(threadId: Long, updatedAt: Long) = Unit

    private fun append(role: MessageRole, content: String, model: String?) {
        val nextId = messages.value.size + 1L
        messages.value = messages.value + Message(
            id = nextId,
            threadId = 1L,
            role = role,
            content = content,
            provider = if (role == MessageRole.ASSISTANT) ModelProvider.OPEN_ROUTER else null,
            model = model,
            latencyMs = if (role == MessageRole.ASSISTANT) 100L else null,
            estimatedCost = if (role == MessageRole.ASSISTANT) 0.001 else null,
            createdAt = nextId,
        )
    }
}

private fun userMessage(id: Long, content: String): Message = Message(
    id = id,
    threadId = 1L,
    role = MessageRole.USER,
    content = content,
    provider = null,
    model = "model-a",
    latencyMs = null,
    estimatedCost = null,
    createdAt = id,
)

private fun model(id: String): ModelCatalogEntry = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.OPEN_ROUTER,
    model = id,
    label = id,
    supportsChat = true,
    supportsCompare = true,
)
