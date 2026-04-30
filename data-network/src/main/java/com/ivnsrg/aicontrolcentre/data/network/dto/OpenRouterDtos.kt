package com.ivnsrg.aicontrolcentre.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
)

@Serializable
data class OpenRouterKeyInfoResponse(
    val data: OpenRouterKeyInfoDto? = null,
)

@Serializable
data class OpenRouterKeyInfoDto(
    val label: String? = null,
    val limit: Double? = null,
    @SerialName("limit_remaining") val limitRemaining: Double? = null,
    @SerialName("limit_reset") val limitReset: String? = null,
    @SerialName("usage_daily") val usageDaily: Double? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean = true,
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
    val stream: Boolean = false,
)

@Serializable
data class OpenRouterMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class OpenRouterChatResponse(
    val id: String? = null,
    val choices: List<OpenRouterChoiceDto> = emptyList(),
    val model: String? = null,
    val usage: OpenRouterUsageDto? = null,
)

@Serializable
data class OpenRouterChoiceDto(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: OpenRouterMessageDto? = null,
)

@Serializable
data class OpenRouterStreamChunkDto(
    val id: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val choices: List<OpenRouterStreamChoiceDto> = emptyList(),
    val usage: OpenRouterUsageDto? = null,
    val error: OpenRouterErrorDto? = null,
)

@Serializable
data class OpenRouterStreamChoiceDto(
    val delta: OpenRouterStreamDeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenRouterStreamDeltaDto(
    val content: String? = null,
)

@Serializable
data class OpenRouterErrorDto(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class OpenRouterErrorResponse(
    val error: OpenRouterErrorDto? = null,
)

@Serializable
data class OpenRouterUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val cost: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
)
