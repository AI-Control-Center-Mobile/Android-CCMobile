package com.ivnsrg.aicontrolcentre.data.storage.mapper

import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.Project
import com.ivnsrg.aicontrolcentre.core.model.Thread
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.MessageEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ProjectEntity
import com.ivnsrg.aicontrolcentre.data.storage.entity.ThreadEntity

fun ProjectEntity.toDomain() = Project(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ThreadEntity.toDomain() = Thread(
    id = id,
    projectId = projectId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MessageEntity.toDomain() = Message(
    id = id,
    threadId = threadId,
    role = MessageRole.valueOf(role),
    content = content,
    provider = provider?.let(ModelProvider::valueOf),
    model = model,
    latencyMs = latencyMs,
    estimatedCost = estimatedCost,
    createdAt = createdAt,
)

fun CachedModelEntity.toDomain() = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.valueOf(provider),
    model = model,
    label = label,
    supportsChat = supportsChat,
    supportsCompare = supportsCompare,
)
