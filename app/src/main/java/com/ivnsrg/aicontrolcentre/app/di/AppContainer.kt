package com.ivnsrg.aicontrolcentre.app.di

import android.content.Context
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterDiagnosticsRepository
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaRepository
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createUnifiedChatRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createUnifiedCompareRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createUnifiedModelsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createOpenRouterDiagnosticsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.createProviderQuotaRepository
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
    val openRouterDiagnosticsRepository: OpenRouterDiagnosticsRepository
    val providerQuotaRepository: ProviderQuotaRepository
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

    override val projectsRepository: ProjectsRepository by lazy {
        DefaultProjectsRepository(database.projectsDao())
    }
    override val threadsRepository: ThreadsRepository by lazy {
        DefaultThreadsRepository(
            threadsDao = database.threadsDao(),
            messagesDao = database.messagesDao(),
            projectsDao = database.projectsDao(),
        )
    }
    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(database, secureApiKeyStorage, preferencesStore)
    }
    override val openRouterDiagnosticsRepository: OpenRouterDiagnosticsRepository by lazy {
        createOpenRouterDiagnosticsRepository(settingsRepository)
    }
    override val providerQuotaRepository: ProviderQuotaRepository by lazy {
        createProviderQuotaRepository(
            settingsRepository = settingsRepository,
            appPreferencesStore = preferencesStore,
            openRouterDiagnosticsRepository = openRouterDiagnosticsRepository,
        )
    }
    override val modelsRepository: ModelsRepository by lazy {
        createUnifiedModelsRepository(database.modelsDao(), settingsRepository)
    }
    override val chatRepository: ChatRepository by lazy {
        createUnifiedChatRepository(
            settingsRepository = settingsRepository,
            providerQuotaRepository = providerQuotaRepository,
        )
    }
    override val compareRepository: CompareRepository by lazy {
        createUnifiedCompareRepository(
            settingsRepository = settingsRepository,
            providerQuotaRepository = providerQuotaRepository,
        )
    }
}
