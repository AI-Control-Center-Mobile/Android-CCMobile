package com.ivnsrg.aicontrolcentre.app.di

import android.content.Context
import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareResultPayload
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatRequest
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterMessageDto
import com.ivnsrg.aicontrolcentre.data.network.mapper.toAssistantMessageDraft
import com.ivnsrg.aicontrolcentre.data.network.mapper.toDomain
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabase
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabaseFactory
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import com.ivnsrg.aicontrolcentre.data.storage.preferences.AppPreferencesStore
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultProjectsRepository
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultSettingsRepository
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultThreadsRepository
import com.ivnsrg.aicontrolcentre.data.storage.security.SecureApiKeyStorage
import kotlin.system.measureTimeMillis

interface AppContainer {
    val projectsRepository: ProjectsRepository
    val threadsRepository: ThreadsRepository
    val settingsRepository: SettingsRepository
    val modelsRepository: ModelsRepository
    val chatRepository: ChatRepository
    val compareRepository: CompareRepository
}

class DefaultAppContainer(
    private val context: Context,
) : AppContainer {
    private val database: AppDatabase by lazy {
        AppDatabaseFactory.create(context)
    }

    private val secureApiKeyStorage by lazy { SecureApiKeyStorage(context) }
    private val preferencesStore by lazy { AppPreferencesStore(context) }
    private val openRouterService: OpenRouterService by lazy { OpenRouterNetworkFactory.createService() }

    override val projectsRepository: ProjectsRepository by lazy {
        DefaultProjectsRepository(database.projectsDao())
    }
    override val threadsRepository: ThreadsRepository by lazy {
        DefaultThreadsRepository(database.threadsDao(), database.messagesDao())
    }
    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(database, secureApiKeyStorage, preferencesStore)
    }
    override val modelsRepository: ModelsRepository by lazy {
        DefaultModelsRepository(database.modelsDao(), openRouterService)
    }
    override val chatRepository: ChatRepository by lazy {
        DefaultChatRepository(openRouterService, settingsRepository)
    }
    override val compareRepository: CompareRepository by lazy {
        DefaultCompareRepository(openRouterService, settingsRepository)
    }
}

private class DefaultModelsRepository(
    private val modelsDao: ModelsDao,
    private val service: OpenRouterService,
) : ModelsRepository {
    override suspend fun getCachedModels(): List<ModelCatalogEntry> =
        modelsDao.getAll().map {
            ModelCatalogEntry(
                id = it.id,
                provider = ModelProvider.valueOf(it.provider),
                model = it.model,
                label = it.label,
                supportsChat = it.supportsChat,
                supportsCompare = it.supportsCompare,
            )
        }

    override suspend fun refreshModels(apiKey: String): List<ModelCatalogEntry> {
        val models = service.getModels("Bearer $apiKey").data.map { it.toDomain() }
        modelsDao.clear()
        modelsDao.insertAll(
            models.map {
                CachedModelEntity(
                    id = it.id,
                    provider = it.provider.name,
                    model = it.model,
                    label = it.label,
                    supportsChat = it.supportsChat,
                    supportsCompare = it.supportsCompare,
                    cachedAt = System.currentTimeMillis(),
                )
            },
        )
        return models
    }
}

private class DefaultChatRepository(
    private val service: OpenRouterService,
    private val settingsRepository: SettingsRepository,
) : ChatRepository {
    override suspend fun sendMessage(threadId: Long, modelId: String, prompt: String): AssistantMessageDraft {
        val apiKey = requireNotNull(settingsRepository.getApiKey()) { "Missing API key" }
        var response = com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse()
        val latency = measureTimeMillis {
            response = service.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = OpenRouterChatRequest(
                    model = modelId,
                    messages = listOf(OpenRouterMessageDto(role = "user", content = prompt)),
                ),
            )
        }
        return response.toAssistantMessageDraft(modelId, latency)
    }
}

private class DefaultCompareRepository(
    private val service: OpenRouterService,
    private val settingsRepository: SettingsRepository,
) : CompareRepository {
    override suspend fun compare(
        threadId: Long,
        modelA: String,
        modelB: String,
        prompt: String,
    ): CompareResultPayload {
        val apiKey = requireNotNull(settingsRepository.getApiKey()) { "Missing API key" }
        val firstLatency: Long
        val firstResponse: com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
        var tempFirst = com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse()
        firstLatency = measureTimeMillis {
            tempFirst = service.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = OpenRouterChatRequest(
                    model = modelA,
                    messages = listOf(OpenRouterMessageDto(role = "user", content = prompt)),
                ),
            )
        }
        firstResponse = tempFirst

        val secondLatency: Long
        val secondResponse: com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
        var tempSecond = com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse()
        secondLatency = measureTimeMillis {
            tempSecond = service.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = OpenRouterChatRequest(
                    model = modelB,
                    messages = listOf(OpenRouterMessageDto(role = "user", content = prompt)),
                ),
            )
        }
        secondResponse = tempSecond

        return CompareResultPayload(
            first = firstResponse.toAssistantMessageDraft(modelA, firstLatency),
            second = secondResponse.toAssistantMessageDraft(modelB, secondLatency),
        )
    }
}
