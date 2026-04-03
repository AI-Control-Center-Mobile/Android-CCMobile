package com.ivnsrg.aicontrolcentre.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
)

@Serializable
data class OpenRouterModelDto(
    val id: String,
    val name: String? = null,
)

@Serializable
data class OpenRouterChatRequest(
    val model: String,
    val messages: List<OpenRouterMessageDto>,
)

@Serializable
data class OpenRouterMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoiceDto> = emptyList(),
    val usage: OpenRouterUsageDto? = null,
)

@Serializable
data class OpenRouterChoiceDto(
    val message: OpenRouterMessageDto? = null,
)

@Serializable
data class OpenRouterUsageDto(
    @SerialName("total_cost") val totalCost: Double? = null,
)
