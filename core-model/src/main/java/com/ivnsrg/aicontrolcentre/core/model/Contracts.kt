package com.ivnsrg.aicontrolcentre.core.model

import kotlinx.coroutines.flow.Flow

data class Project(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Thread(
    val id: Long,
    val projectId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Message(
    val id: Long,
    val threadId: Long,
    val role: MessageRole,
    val content: String,
    val provider: ModelProvider?,
    val model: String?,
    val latencyMs: Long?,
    val estimatedCost: Double?,
    val createdAt: Long,
)

data class ModelCatalogEntry(
    val id: String,
    val provider: ModelProvider,
    val model: String,
    val label: String,
    val supportsChat: Boolean,
    val supportsCompare: Boolean,
)

data class ChatRequestUiModel(
    val threadId: Long,
    val modelId: String,
    val prompt: String,
)

data class CompareRequestUiModel(
    val threadId: Long,
    val modelA: String,
    val modelB: String,
    val prompt: String,
)

data class OpenRouterKeyDiagnostics(
    val label: String? = null,
    val isFreeTier: Boolean = true,
    val limitRemaining: Double? = null,
    val usageDaily: Double = 0.0,
    val limitReset: String? = null,
)

data class ProviderQuotaValue(
    val label: String,
    val value: String,
)

enum class ProviderQuotaStatus {
    AVAILABLE,
    UNAVAILABLE,
    ERROR,
}

enum class ProviderQuotaSource {
    LIVE,
    SNAPSHOT,
}

data class ProviderQuotaSnapshot(
    val provider: ModelProvider,
    val status: ProviderQuotaStatus,
    val headline: String,
    val values: List<ProviderQuotaValue> = emptyList(),
    val detail: String? = null,
    val updatedAt: Long? = null,
    val source: ProviderQuotaSource = ProviderQuotaSource.LIVE,
)

data class ProviderApiKey(
    val provider: ModelProvider,
    val key: String,
)

data class AssistantMessageDraft(
    val content: String,
    val provider: ModelProvider,
    val model: String,
    val latencyMs: Long?,
    val estimatedCost: Double?,
)

data class CompareResultPayload(
    val first: AssistantMessageDraft,
    val second: AssistantMessageDraft,
)

sealed interface AssistantStreamEvent {
    data class Streaming(
        val accumulatedContent: String,
        val isProcessing: Boolean,
    ) : AssistantStreamEvent

    data class Completed(
        val draft: AssistantMessageDraft,
    ) : AssistantStreamEvent
}

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class ModelProvider {
    OPEN_ROUTER,
    GROQ,
    SILICON_FLOW,
    ;

    val displayName: String
        get() = when (this) {
            OPEN_ROUTER -> "OpenRouter"
            GROQ -> "Groq"
            SILICON_FLOW -> "SiliconFlow"
        }

    val keyLabel: String
        get() = "$displayName API key"

    val keyPlaceholder: String
        get() = when (this) {
            OPEN_ROUTER -> "sk-or-v1-..."
            GROQ -> "gsk_..."
            SILICON_FLOW -> "sk-..."
        }
}

enum class CompareWinner {
    FIRST,
    SECOND,
}

enum class ModelPickerMode {
    CHAT,
    COMPARE_A,
    COMPARE_B,
    ;

    val supportsCompare: Boolean
        get() = this != CHAT
}

sealed interface UiError {
    data object None : UiError
    data object MissingApiKey : UiError
    data class Validation(val message: String) : UiError
    data class Network(val message: String) : UiError
    data class Provider(val message: String) : UiError
    data class Unknown(val message: String) : UiError
}

class UiException(
    val error: UiError,
    cause: Throwable? = null,
) : RuntimeException(
    when (error) {
        UiError.None -> "Unknown UI error"
        UiError.MissingApiKey -> "Missing API key"
        is UiError.Network -> error.message
        is UiError.Provider -> error.message
        is UiError.Unknown -> error.message
        is UiError.Validation -> error.message
    },
    cause,
)

interface ProjectsRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun createProject(title: String): Long
    suspend fun getProject(projectId: Long): Project?
    suspend fun deleteProject(projectId: Long)
}

interface ThreadsRepository {
    fun observeThreads(projectId: Long): Flow<List<Thread>>
    suspend fun createThread(projectId: Long, title: String? = null): Long
    suspend fun deleteThread(threadId: Long)
    fun observeMessages(threadId: Long): Flow<List<Message>>
    suspend fun insertUserMessage(
        threadId: Long,
        content: String,
        targetModel: String,
        targetProvider: ModelProvider?,
    )
    suspend fun insertAssistantMessage(
        threadId: Long,
        content: String,
        provider: ModelProvider,
        model: String,
        latencyMs: Long?,
        estimatedCost: Double?,
    )

    suspend fun updateThreadMetadata(threadId: Long, updatedAt: Long = System.currentTimeMillis())
}

interface SettingsRepository {
    suspend fun getProviderKeys(): List<ProviderApiKey>
    suspend fun getApiKey(provider: ModelProvider): String?
    suspend fun saveApiKey(provider: ModelProvider, key: String)
    suspend fun clearApiKey(provider: ModelProvider)
    suspend fun clearAllApiKeys()
    suspend fun clearAllLocalData()

    suspend fun hasAnyApiKeys(): Boolean = getProviderKeys().isNotEmpty()
}

interface ModelsRepository {
    suspend fun getCachedModels(): List<ModelCatalogEntry>
    suspend fun refreshModels(forceRefresh: Boolean = false): List<ModelCatalogEntry>
}

interface OpenRouterDiagnosticsRepository {
    suspend fun getCurrentKeyDiagnostics(): OpenRouterKeyDiagnostics
}

interface ProviderQuotaRepository {
    suspend fun getQuotaSnapshots(): List<ProviderQuotaSnapshot>
    suspend fun refreshQuotaSnapshots(): List<ProviderQuotaSnapshot>
    suspend fun recordRateLimitSnapshot(
        provider: ModelProvider,
        remainingRequests: String?,
        remainingTokens: String?,
        resetRequests: String?,
        resetTokens: String?,
    )
}

interface ChatRepository {
    suspend fun sendMessage(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft

    fun streamMessage(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent>
}

interface CompareRepository {
    suspend fun compare(
        threadId: Long,
        providerA: ModelProvider,
        modelA: String,
        providerB: ModelProvider,
        modelB: String,
        prompt: String,
        history: List<Message>,
    ): CompareResultPayload

    fun streamModelResponse(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent>
}
