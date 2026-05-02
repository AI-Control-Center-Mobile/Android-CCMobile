package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterKeyDiagnostics
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleErrorResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleMessageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleModelDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterKeyInfoDto
import retrofit2.HttpException
import java.io.IOException

fun OpenAiCompatibleModelDto.toDomain(provider: ModelProvider) = ModelCatalogEntry(
    id = "${provider.name}:$id",
    provider = provider,
    model = id,
    label = name ?: id.substringAfterLast('/'),
    supportsChat = true,
    supportsCompare = true,
)

fun OpenRouterKeyInfoDto.toDomain(): OpenRouterKeyDiagnostics = OpenRouterKeyDiagnostics(
    label = label,
    isFreeTier = isFreeTier,
    limitRemaining = limitRemaining ?: limit,
    usageDaily = usageDaily ?: 0.0,
    limitReset = limitReset,
)

fun OpenAiCompatibleChatResponse.toAssistantMessageDraft(
    provider: ModelProvider,
    requestedModel: String,
    latencyMs: Long,
): AssistantMessageDraft {
    val content = choices.firstOrNull()?.message?.content?.trim().orEmpty()
    if (content.isBlank()) {
        throw UiException(UiError.Provider("Провайдер вернул пустой ответ"))
    }

    return AssistantMessageDraft(
        content = content,
        provider = provider,
        model = model ?: requestedModel,
        latencyMs = latencyMs,
        estimatedCost = usage.toEstimatedCost(),
    )
}

fun OpenAiCompatibleChatResponse.toProviderMessage(): String =
    choices.firstOrNull()?.message?.content?.trim().orEmpty()

fun mapNetworkFailure(throwable: Throwable): UiException {
    if (throwable is UiException) return throwable

    return when (throwable) {
        is HttpException -> {
            when (throwable.code()) {
                401, 403 -> UiException(UiError.MissingApiKey, throwable)
                408, 429, 500, 502, 503 -> UiException(
                    UiError.Network("Сервис OpenRouter временно недоступен (${throwable.code()})"),
                    throwable,
                )
                else -> UiException(
                    UiError.Provider("Провайдер вернул ошибку ${throwable.code()}"),
                    throwable,
                )
            }
        }

        is IOException -> UiException(UiError.Network("Проверь подключение к сети и повтори попытку"), throwable)
        else -> UiException(UiError.Unknown(throwable.message ?: "Неизвестная ошибка"), throwable)
    }
}

fun com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleUsageDto?.toEstimatedCost(): Double? {
    if (this == null) return null
    return cost ?: totalCost ?: estimateCostFromTokens()
}

private fun com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleUsageDto.estimateCostFromTokens(): Double? {
    val total = totalTokens ?: listOfNotNull(promptTokens, completionTokens).takeIf { it.isNotEmpty() }?.sum()
    return total?.takeIf { it > 0 }?.let { tokens -> tokens * TOKEN_COST_FALLBACK_USD }
}

fun String.extractProviderMessage(json: kotlinx.serialization.json.Json): String? =
    takeIf { it.isNotBlank() }
        ?.let {
            runCatching { json.decodeFromString(OpenAiCompatibleErrorResponse.serializer(), it) }
                .getOrNull()
                ?.error
                ?.message
                ?.trim()
        }
        ?.takeIf { it.isNotBlank() }

fun buildOpenAiCompatibleMessages(
    history: List<com.ivnsrg.aicontrolcentre.core.model.Message>,
    prompt: String,
): List<OpenAiCompatibleMessageDto> {
    val trimmedPrompt = prompt.trim()
    val messages = history
        .filter { it.content.isNotBlank() }
        .map { message ->
            OpenAiCompatibleMessageDto(
                role = when (message.role) {
                    com.ivnsrg.aicontrolcentre.core.model.MessageRole.USER -> "user"
                    com.ivnsrg.aicontrolcentre.core.model.MessageRole.ASSISTANT -> "assistant"
                },
                content = message.content,
            )
        }
        .toMutableList()

    val latestUserPrompt = history
        .asReversed()
        .firstOrNull { it.role == com.ivnsrg.aicontrolcentre.core.model.MessageRole.USER }
        ?.content
        ?.trim()

    if (trimmedPrompt.isNotBlank() && latestUserPrompt != trimmedPrompt) {
        messages += OpenAiCompatibleMessageDto(role = "user", content = trimmedPrompt)
    }

    return messages
}

private const val TOKEN_COST_FALLBACK_USD = 0.000001
