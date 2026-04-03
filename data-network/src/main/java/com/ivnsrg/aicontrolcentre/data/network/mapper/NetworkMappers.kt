package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelDto

fun OpenRouterModelDto.toDomain() = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.OPEN_ROUTER,
    model = id,
    label = name ?: id,
    supportsChat = true,
    supportsCompare = true,
)

fun OpenRouterChatResponse.toAssistantMessageDraft(
    requestedModel: String,
    latencyMs: Long,
) = AssistantMessageDraft(
    content = choices.firstOrNull()?.message?.content.orEmpty(),
    provider = ModelProvider.OPEN_ROUTER,
    model = requestedModel,
    latencyMs = latencyMs,
    estimatedCost = usage?.totalCost,
)
