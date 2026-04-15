package com.ivnsrg.aicontrolcentre.app.di

import android.content.Context
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.repository.OpenRouterModelsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createOpenRouterChatRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createOpenRouterCompareRepository
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabase
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabaseFactory
import com.ivnsrg.aicontrolcentre.data.storage.preferences.AppPreferencesStore
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultProjectsRepository
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultSettingsRepository
import com.ivnsrg.aicontrolcentre.data.storage.repository.DefaultThreadsRepository
import com.ivnsrg.aicontrolcentre.data.storage.security.SecureApiKeyStorage

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
        OpenRouterModelsRepository(database.modelsDao(), openRouterService, settingsRepository)
    }
    override val chatRepository: ChatRepository by lazy {
        createOpenRouterChatRepository(settingsRepository)
    }
    override val compareRepository: CompareRepository by lazy {
        createOpenRouterCompareRepository(settingsRepository)
    }
}
