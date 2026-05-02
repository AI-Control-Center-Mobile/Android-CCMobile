package com.ivnsrg.aicontrolcentre.data.storage.repository

import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.Project
import com.ivnsrg.aicontrolcentre.core.model.ProviderApiKey
import com.ivnsrg.aicontrolcentre.core.model.ProjectsRepository
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.core.model.ThreadsRepository
import com.ivnsrg.aicontrolcentre.data.storage.dao.MessagesDao
import com.ivnsrg.aicontrolcentre.data.storage.dao.ProjectsDao
import com.ivnsrg.aicontrolcentre.data.storage.dao.ThreadsDao
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabase
import com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity
import com.ivnsrg.aicontrolcentre.data.storage.mapper.toDomain
import com.ivnsrg.aicontrolcentre.data.storage.preferences.AppPreferencesStore
import com.ivnsrg.aicontrolcentre.data.storage.security.SecureApiKeyStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultProjectsRepository(
    private val projectsDao: ProjectsDao,
) : ProjectsRepository {
    override fun observeProjects(): Flow<List<Project>> =
        projectsDao.observeProjects().map { items -> items.map { it.toDomain() } }

    override suspend fun createProject(title: String): Long {
        val now = System.currentTimeMillis()
        return projectsDao.insert(
            ProjectEntity(
                title = title,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun getProject(projectId: Long): Project? = projectsDao.getProject(projectId)?.toDomain()

    override suspend fun deleteProject(projectId: Long) {
        projectsDao.deleteProject(projectId)
    }
}

class DefaultThreadsRepository(
    private val threadsDao: ThreadsDao,
    private val messagesDao: MessagesDao,
    private val projectsDao: ProjectsDao? = null,
) : ThreadsRepository {
    override fun observeThreads(projectId: Long): Flow<List<Thread>> =
        threadsDao.observeThreads(projectId).map { items -> items.map { it.toDomain() } }

    override suspend fun createThread(projectId: Long, title: String?): Long {
        val now = System.currentTimeMillis()
        val threadId = threadsDao.insert(
            ThreadEntity(
                projectId = projectId,
                title = title?.takeIf { it.isNotBlank() } ?: "New Thread",
                createdAt = now,
                updatedAt = now,
            ),
        )
        projectsDao?.updateProjectUpdatedAt(projectId, now)
        return threadId
    }

    override suspend fun deleteThread(threadId: Long) {
        threadsDao.deleteThread(threadId)
    }

    override fun observeMessages(threadId: Long) =
        messagesDao.observeMessages(threadId).map { items -> items.map { it.toDomain() } }

    override suspend fun insertUserMessage(
        threadId: Long,
        content: String,
        targetModel: String,
        targetProvider: ModelProvider?,
    ) {
        messagesDao.insert(
            MessageEntity(
                threadId = threadId,
                role = MessageRole.USER.name,
                content = content,
                provider = targetProvider?.name,
                model = targetModel,
                latencyMs = null,
                estimatedCost = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        updateThreadMetadata(threadId)
    }

    override suspend fun insertAssistantMessage(
        threadId: Long,
        content: String,
        provider: ModelProvider,
        model: String,
        latencyMs: Long?,
        estimatedCost: Double?,
    ) {
        messagesDao.insert(
            MessageEntity(
                threadId = threadId,
                role = MessageRole.ASSISTANT.name,
                content = content,
                provider = provider.name,
                model = model,
                latencyMs = latencyMs,
                estimatedCost = estimatedCost,
                createdAt = System.currentTimeMillis(),
            ),
        )
        updateThreadMetadata(threadId)
    }

    override suspend fun updateThreadMetadata(threadId: Long, updatedAt: Long) {
        threadsDao.updateThreadUpdatedAt(threadId, updatedAt)
        threadsDao.updateParentProjectUpdatedAt(threadId, updatedAt)
    }
}

class DefaultSettingsRepository(
    private val database: AppDatabase,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val appPreferencesStore: AppPreferencesStore,
    private val demoWorkspaceSeeder: DemoWorkspaceSeeder = DemoWorkspaceSeeder(database),
) : SettingsRepository {
    override suspend fun getProviderKeys(): List<ProviderApiKey> = secureApiKeyStorage.getProviderKeys()

    override suspend fun getApiKey(provider: ModelProvider): String? {
        val apiKey = secureApiKeyStorage.getApiKey(provider)
        if (!apiKey.isNullOrBlank()) {
            demoWorkspaceSeeder.seedIfEmpty()
        }
        return apiKey
    }

    override suspend fun saveApiKey(provider: ModelProvider, key: String) {
        secureApiKeyStorage.saveApiKey(provider, key)
        demoWorkspaceSeeder.seedIfEmpty()
    }

    override suspend fun clearApiKey(provider: ModelProvider) {
        secureApiKeyStorage.clearApiKey(provider)
    }

    override suspend fun clearAllApiKeys() {
        secureApiKeyStorage.clearAllApiKeys()
    }

    override suspend fun clearAllLocalData() {
        secureApiKeyStorage.clearAllApiKeys()
        database.clearAllTables()
        appPreferencesStore.clear()
    }
}
