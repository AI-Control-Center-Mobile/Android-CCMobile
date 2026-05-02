package com.ivnsrg.aicontrolcentre.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiCompatibleModelsResponse(
    val data: List<OpenAiCompatibleModelDto> = emptyList(),
)

@Serializable
data class OpenAiCompatibleModelDto(
    val id: String,
    val name: String? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
)

@Serializable
data class OpenAiCompatibleChatRequest(
    val model: String,
    val messages: List<OpenAiCompatibleMessageDto>,
    val stream: Boolean = false,
)

@Serializable
data class OpenAiCompatibleMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiCompatibleChatResponse(
    val id: String? = null,
    val choices: List<OpenAiCompatibleChoiceDto> = emptyList(),
    val model: String? = null,
    val usage: OpenAiCompatibleUsageDto? = null,
)

@Serializable
data class OpenAiCompatibleChoiceDto(
    @SerialName("finish_reason") val finishReason: String? = null,
    val message: OpenAiCompatibleMessageDto? = null,
)

@Serializable
data class OpenAiCompatibleStreamChunkDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAiCompatibleStreamChoiceDto> = emptyList(),
    val usage: OpenAiCompatibleUsageDto? = null,
    val error: OpenAiCompatibleErrorDto? = null,
)

@Serializable
data class OpenAiCompatibleStreamChoiceDto(
    val delta: OpenAiCompatibleStreamDeltaDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenAiCompatibleStreamDeltaDto(
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
data class OpenAiCompatibleErrorDto(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class OpenAiCompatibleErrorResponse(
    val error: OpenAiCompatibleErrorDto? = null,
)

@Serializable
data class OpenAiCompatibleUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val cost: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
)
