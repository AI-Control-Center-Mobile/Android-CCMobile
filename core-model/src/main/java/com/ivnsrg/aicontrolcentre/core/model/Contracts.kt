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

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class ModelProvider {
    OPEN_ROUTER,
}

enum class CompareWinner {
    FIRST,
    SECOND,
}

sealed interface UiError {
    data object None : UiError
    data object MissingApiKey : UiError
    data class Validation(val message: String) : UiError
    data class Network(val message: String) : UiError
    data class Provider(val message: String) : UiError
    data class Unknown(val message: String) : UiError
}

interface ProjectsRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun createProject(title: String): Long
    suspend fun getProject(projectId: Long): Project?
}

interface ThreadsRepository {
    fun observeThreads(projectId: Long): Flow<List<Thread>>
    suspend fun createThread(projectId: Long, title: String? = null): Long
    fun observeMessages(threadId: Long): Flow<List<Message>>
    suspend fun insertUserMessage(threadId: Long, content: String, targetModel: String)
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
    suspend fun getApiKey(): String?
    suspend fun saveApiKey(key: String)
    suspend fun clearApiKey()
    suspend fun clearAllLocalData()
}

interface ModelsRepository {
    suspend fun getCachedModels(): List<ModelCatalogEntry>
    suspend fun refreshModels(apiKey: String): List<ModelCatalogEntry>
}

interface ChatRepository {
    suspend fun sendMessage(threadId: Long, modelId: String, prompt: String): AssistantMessageDraft
}

interface CompareRepository {
    suspend fun compare(
        threadId: Long,
        modelA: String,
        modelB: String,
        prompt: String,
    ): CompareResultPayload
}
