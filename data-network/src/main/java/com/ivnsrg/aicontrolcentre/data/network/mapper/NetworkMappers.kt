package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterKeyDiagnostics
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterKeyInfoDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelDto
import retrofit2.HttpException
import java.io.IOException

fun OpenRouterModelDto.toDomain() = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.OPEN_ROUTER,
    model = id,
    label = name ?: id,
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

fun OpenRouterChatResponse.toAssistantMessageDraft(
    requestedModel: String,
    latencyMs: Long,
): AssistantMessageDraft {
    val content = choices.firstOrNull()?.message?.content?.trim().orEmpty()
    if (content.isBlank()) {
        throw UiException(UiError.Provider("Провайдер вернул пустой ответ"))
    }

    return AssistantMessageDraft(
        content = content,
        provider = ModelProvider.OPEN_ROUTER,
        model = model ?: requestedModel,
        latencyMs = latencyMs,
        estimatedCost = usage.toEstimatedCost(),
    )
}

fun OpenRouterChatResponse.toProviderMessage(): String =
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

fun com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterUsageDto?.toEstimatedCost(): Double? {
    if (this == null) return null
    return cost ?: totalCost ?: estimateCostFromTokens()
}

private fun com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterUsageDto.estimateCostFromTokens(): Double? {
    val total = totalTokens ?: listOfNotNull(promptTokens, completionTokens).takeIf { it.isNotEmpty() }?.sum()
    return total?.takeIf { it > 0 }?.let { tokens -> tokens * TOKEN_COST_FALLBACK_USD }
}

private const val TOKEN_COST_FALLBACK_USD = 0.000001
