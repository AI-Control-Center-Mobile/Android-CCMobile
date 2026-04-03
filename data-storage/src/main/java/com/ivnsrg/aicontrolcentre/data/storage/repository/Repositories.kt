package com.ivnsrg.aicontrolcentre.data.storage.repository

import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.Project
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
}

class DefaultThreadsRepository(
    private val threadsDao: ThreadsDao,
    private val messagesDao: MessagesDao,
) : ThreadsRepository {
    override fun observeThreads(projectId: Long): Flow<List<Thread>> =
        threadsDao.observeThreads(projectId).map { items -> items.map { it.toDomain() } }

    override suspend fun createThread(projectId: Long, title: String?): Long {
        val now = System.currentTimeMillis()
        return threadsDao.insert(
            ThreadEntity(
                projectId = projectId,
                title = title ?: "Thread ${now.toString().takeLast(4)}",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override fun observeMessages(threadId: Long) =
        messagesDao.observeMessages(threadId).map { items -> items.map { it.toDomain() } }

    override suspend fun insertUserMessage(threadId: Long, content: String, targetModel: String) {
        messagesDao.insert(
            MessageEntity(
                threadId = threadId,
                role = MessageRole.USER.name,
                content = content,
                provider = null,
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
    }
}

class DefaultSettingsRepository(
    private val database: AppDatabase,
    private val secureApiKeyStorage: SecureApiKeyStorage,
    private val appPreferencesStore: AppPreferencesStore,
) : SettingsRepository {
    override suspend fun getApiKey(): String? = secureApiKeyStorage.getApiKey()

    override suspend fun saveApiKey(key: String) {
        secureApiKeyStorage.saveApiKey(key)
    }

    override suspend fun clearApiKey() {
        secureApiKeyStorage.clearApiKey()
    }

    override suspend fun clearAllLocalData() {
        secureApiKeyStorage.clearApiKey()
        database.clearAllTables()
        appPreferencesStore.clear()
    }
}
