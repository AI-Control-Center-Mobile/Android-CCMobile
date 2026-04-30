package com.ivnsrg.aicontrolcentre.data.storage.repository

import androidx.room.withTransaction
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.data.storage.db.AppDatabase
import com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity

class DemoWorkspaceSeeder(
    private val database: AppDatabase,
) {
    suspend fun seedIfEmpty() {
        if (database.projectsDao().countProjects() > 0) return

        database.withTransaction {
            val now = System.currentTimeMillis()

            val projectOneId = database.projectsDao().insert(
                ProjectEntity(
                    title = "UI System Refactor",
                    createdAt = now - 86_400_000L,
                    updatedAt = now - 20 * 60_000L,
                ),
            )

            val projectTwoId = database.projectsDao().insert(
                ProjectEntity(
                    title = "Market Analysis v2",
                    createdAt = now - 172_800_000L,
                    updatedAt = now - 5 * 3_600_000L,
                ),
            )

            val typographyThreadId = database.threadsDao().insert(
                ThreadEntity(
                    projectId = projectOneId,
                    title = "Typography Audit",
                    createdAt = now - 3_600_000L,
                    updatedAt = now - 20 * 60_000L,
                ),
            )

            val paletteThreadId = database.threadsDao().insert(
                ThreadEntity(
                    projectId = projectOneId,
                    title = "Color Palette Refinement",
                    createdAt = now - 7_200_000L,
                    updatedAt = now - 90 * 60_000L,
                ),
            )

            val marketThreadId = database.threadsDao().insert(
                ThreadEntity(
                    projectId = projectTwoId,
                    title = "Pricing Snapshot",
                    createdAt = now - 21_600_000L,
                    updatedAt = now - 5 * 3_600_000L,
                ),
            )

            database.messagesDao().insert(
                MessageEntity(
                    threadId = typographyThreadId,
                    role = MessageRole.USER.name,
                    content = "Evaluate font-pairings for a terminal-inspired IDE interface.",
                    provider = null,
                    model = "anthropic/claude-3.5-sonnet",
                    latencyMs = null,
                    estimatedCost = null,
                    createdAt = now - 25 * 60_000L,
                ),
            )
            database.messagesDao().insert(
                MessageEntity(
                    threadId = typographyThreadId,
                    role = MessageRole.ASSISTANT.name,
                    content = "Use General Sans for interface copy and JetBrains Mono for dense metadata blocks.",
                    provider = ModelProvider.OPEN_ROUTER.name,
                    model = "anthropic/claude-3.5-sonnet",
                    latencyMs = 842L,
                    estimatedCost = 0.0024,
                    createdAt = now - 20 * 60_000L,
                ),
            )

            database.messagesDao().insert(
                MessageEntity(
                    threadId = paletteThreadId,
                    role = MessageRole.USER.name,
                    content = "Generate a balanced dark-mode palette for dashboards with AI metadata chips.",
                    provider = null,
                    model = "openai/gpt-4o-mini",
                    latencyMs = null,
                    estimatedCost = null,
                    createdAt = now - 100 * 60_000L,
                ),
            )
            database.messagesDao().insert(
                MessageEntity(
                    threadId = paletteThreadId,
                    role = MessageRole.ASSISTANT.name,
                    content = "Anchor the palette with deep navy surfaces, cool slate borders, and one high-contrast mint accent.",
                    provider = ModelProvider.OPEN_ROUTER.name,
                    model = "openai/gpt-4o-mini",
                    latencyMs = 1100L,
                    estimatedCost = 0.0018,
                    createdAt = now - 90 * 60_000L,
                ),
            )

            database.messagesDao().insert(
                MessageEntity(
                    threadId = marketThreadId,
                    role = MessageRole.USER.name,
                    content = "Summarize the latest pricing assumptions for the Q3 experiment.",
                    provider = null,
                    model = "meta-llama/llama-3.1-70b-instruct",
                    latencyMs = null,
                    estimatedCost = null,
                    createdAt = now - 6 * 3_600_000L,
                ),
            )
            database.messagesDao().insert(
                MessageEntity(
                    threadId = marketThreadId,
                    role = MessageRole.ASSISTANT.name,
                    content = "Demand is stable, CAC is down 8%, and the current recommendation is to keep the premium tier unchanged.",
                    provider = ModelProvider.OPEN_ROUTER.name,
                    model = "meta-llama/llama-3.1-70b-instruct",
                    latencyMs = 1540L,
                    estimatedCost = 0.0031,
                    createdAt = now - 5 * 3_600_000L,
                ),
            )
        }
    }
}
