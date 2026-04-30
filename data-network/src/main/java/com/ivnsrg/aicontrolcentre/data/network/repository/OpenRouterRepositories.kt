package com.ivnsrg.aicontrolcentre.data.network.repository

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareResultPayload
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterDiagnosticsRepository
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterKeyDiagnostics
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatRequest
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterErrorResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterMessageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterStreamChunkDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterUsageDto
import com.ivnsrg.aicontrolcentre.data.network.mapper.mapNetworkFailure
import com.ivnsrg.aicontrolcentre.data.network.mapper.toAssistantMessageDraft
import com.ivnsrg.aicontrolcentre.data.network.mapper.toDomain
import com.ivnsrg.aicontrolcentre.data.network.mapper.toEstimatedCost
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.system.measureTimeMillis

class OpenRouterModelsRepository(
    private val modelsDao: ModelsDao,
    private val service: OpenRouterService,
    private val settingsRepository: SettingsRepository,
) : ModelsRepository {
    private val refreshMutex = Mutex()

    override suspend fun getCachedModels(): List<ModelCatalogEntry> =
        modelsDao.getAll().map(CachedModelEntity::toDomainModel)

    override suspend fun refreshModels(forceRefresh: Boolean): List<ModelCatalogEntry> {
        if (!forceRefresh) {
            getFreshCachedModels()?.let { return it }
        }

        return refreshMutex.withLock {
            if (!forceRefresh) {
                getFreshCachedModels()?.let { return@withLock it }
            }
            fetchRemoteModels()
        }
    }

    private suspend fun fetchRemoteModels(): List<ModelCatalogEntry> {
        val apiKeys = settingsRepository.getApiKeys().ifEmpty {
            listOfNotNull(settingsRepository.getPrimaryApiKey())
        }
        if (apiKeys.isEmpty()) {
            throw UiException(UiError.MissingApiKey)
        }

        var lastFailure: OpenRouterExecutionException? = null
        apiKeys.forEachIndexed { index, apiKey ->
            try {
                val models = service.getModels(apiKey.toBearerToken())
                    .data
                    .map { it.toDomain() }
                    .sortedBy { it.label.lowercase() }

                modelsDao.clear()
                modelsDao.insertAll(
                    models.map {
                        CachedModelEntity(
                            id = it.id,
                            provider = it.provider.name,
                            model = it.model,
                            label = it.label,
                            supportsChat = it.supportsChat,
                            supportsCompare = it.supportsCompare,
                            cachedAt = System.currentTimeMillis(),
                        )
                    },
                )
                settingsRepository.promoteSuccessfulFallback(index, apiKey)
                return models
            } catch (throwable: Throwable) {
                val failure = throwable.toModelsExecutionException()
                lastFailure = failure
                if (!failure.shouldFailover || index == apiKeys.lastIndex) {
                    throw failure.toUiException()
                }
            }
        }

        throw lastFailure?.toUiException() ?: UiException(UiError.MissingApiKey)
    }

    private suspend fun getFreshCachedModels(): List<ModelCatalogEntry>? {
        val cached = modelsDao.getAll()
        if (cached.isEmpty()) return null

        val newestCachedAt = cached.maxOf { it.cachedAt }
        if (System.currentTimeMillis() - newestCachedAt > MODELS_CACHE_FRESHNESS_MS) {
            return null
        }

        return cached.map(CachedModelEntity::toDomainModel)
    }
}

class DefaultOpenRouterDiagnosticsRepository(
    private val service: OpenRouterService,
    private val settingsRepository: SettingsRepository,
) : OpenRouterDiagnosticsRepository {
    override suspend fun getCurrentKeyDiagnostics(): OpenRouterKeyDiagnostics {
        val apiKeys = settingsRepository.getApiKeys().ifEmpty {
            listOfNotNull(settingsRepository.getPrimaryApiKey())
        }
        if (apiKeys.isEmpty()) {
            throw UiException(UiError.MissingApiKey)
        }

        var lastFailure: OpenRouterExecutionException? = null
        apiKeys.forEachIndexed { index, apiKey ->
            try {
                val response = service.getCurrentKeyInfo(apiKey.toBearerToken())
                val diagnostics = response.data?.toDomain()
                    ?: throw OpenRouterExecutionException(
                        uiError = UiError.Provider("OpenRouter не вернул данные по текущему API key"),
                        shouldFailover = false,
                    )
                settingsRepository.promoteSuccessfulFallback(index, apiKey)
                return diagnostics
            } catch (throwable: Throwable) {
                val failure = throwable.toModelsExecutionException()
                lastFailure = failure
                if (!failure.shouldFailover || index == apiKeys.lastIndex) {
                    throw failure.toUiException()
                }
            }
        }

        throw lastFailure?.toUiException() ?: UiException(UiError.MissingApiKey)
    }
}

class OpenRouterChatRepository internal constructor(
    httpClient: OkHttpClient,
    json: Json,
    settingsRepository: SettingsRepository,
    baseUrl: String = OPEN_ROUTER_CHAT_COMPLETIONS_URL,
) : ChatRepository {
    private val completionExecutor = OpenRouterCompletionExecutor(
        httpClient = httpClient,
        json = json,
        settingsRepository = settingsRepository,
        baseUrl = baseUrl,
    )

    override suspend fun sendMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft = completionExecutor.createCompletion(modelId, prompt, history)

    override fun streamMessage(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = completionExecutor.streamCompletion(modelId, prompt, history)
}

class OpenRouterCompareRepository internal constructor(
    httpClient: OkHttpClient,
    json: Json,
    settingsRepository: SettingsRepository,
    baseUrl: String = OPEN_ROUTER_CHAT_COMPLETIONS_URL,
) : CompareRepository {
    private val completionExecutor = OpenRouterCompletionExecutor(
        httpClient = httpClient,
        json = json,
        settingsRepository = settingsRepository,
        baseUrl = baseUrl,
    )

    override suspend fun compare(
        threadId: Long,
        modelA: String,
        modelB: String,
        prompt: String,
        history: List<Message>,
    ): CompareResultPayload = coroutineScope {
        val first = async { completionExecutor.createCompletion(modelA, prompt, history) }
        val second = async { completionExecutor.createCompletion(modelB, prompt, history) }
        val drafts = awaitAll(first, second)
        CompareResultPayload(
            first = drafts[0],
            second = drafts[1],
        )
    }

    override fun streamModelResponse(
        threadId: Long,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = completionExecutor.streamCompletion(modelId, prompt, history)
}

private class OpenRouterCompletionExecutor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    private val baseUrl: String,
) {
    suspend fun createCompletion(
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft {
        val apiKeys = requireApiKeys()
        var lastFailure: OpenRouterExecutionException? = null

        apiKeys.forEachIndexed { index, apiKey ->
            try {
                val draft = executeCompletion(apiKey, modelId, prompt, history)
                settingsRepository.promoteSuccessfulFallback(index, apiKey)
                return draft
            } catch (failure: OpenRouterExecutionException) {
                lastFailure = failure
                if (!failure.shouldFailover || index == apiKeys.lastIndex) {
                    throw failure.toUiException()
                }
            }
        }

        throw lastFailure?.toUiException() ?: UiException(UiError.MissingApiKey)
    }

    fun streamCompletion(
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        val apiKeys = requireApiKeys()
        var lastFailure: OpenRouterExecutionException? = null

        apiKeys.forEachIndexed { index, apiKey ->
            try {
                emit(AssistantStreamEvent.Streaming(accumulatedContent = "", isProcessing = true))
                streamCompletionAttempt(apiKey, modelId, prompt, history).collect { emit(it) }
                settingsRepository.promoteSuccessfulFallback(index, apiKey)
                return@flow
            } catch (failure: OpenRouterExecutionException) {
                lastFailure = failure
                if (!failure.shouldFailover || index == apiKeys.lastIndex) {
                    throw failure.toUiException()
                }
            }
        }

        throw lastFailure?.toUiException() ?: UiException(UiError.MissingApiKey)
    }

    private suspend fun executeCompletion(
        apiKey: String,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft = withContext(Dispatchers.IO) {
        val request = buildRequest(
            authorization = apiKey.toBearerToken(),
            payload = OpenRouterChatRequest(
                model = modelId,
                messages = buildOpenRouterMessages(history = history, prompt = prompt),
            ),
        )

        var responseModel: OpenRouterChatResponse? = null
        val latencyMs = measureTimeMillis {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw response.toExecutionException(json, modelId)
                }
                val body = response.body?.string().orEmpty()
                responseModel = json.decodeFromString(OpenRouterChatResponse.serializer(), body)
            }
        }

        responseModel?.toAssistantMessageDraft(modelId, latencyMs)
            ?: throw OpenRouterExecutionException(
                uiError = UiError.Provider("Провайдер вернул пустой ответ"),
                shouldFailover = false,
            )
    }

    private fun streamCompletionAttempt(
        apiKey: String,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        val request = buildRequest(
            authorization = apiKey.toBearerToken(),
            payload = OpenRouterChatRequest(
                model = modelId,
                messages = buildOpenRouterMessages(history = history, prompt = prompt),
                stream = true,
            ),
        )

        val startTime = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.toExecutionException(json, modelId)
            }

            val source = response.body?.source()
                ?: throw OpenRouterExecutionException(
                    uiError = UiError.Provider("Провайдер вернул пустой stream"),
                    shouldFailover = false,
                )

            val state = StreamingAccumulator(modelId = modelId)
            val pendingLines = mutableListOf<String>()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) {
                    state.consumeEventLines(pendingLines, json)?.let { emit(it) }
                    pendingLines.clear()
                    if (state.isDone) break
                    continue
                }
                pendingLines += line
            }

            if (pendingLines.isNotEmpty()) {
                state.consumeEventLines(pendingLines, json)?.let { emit(it) }
            }

            if (!state.isDone) {
                throw OpenRouterExecutionException(
                    uiError = UiError.Network("OpenRouter закрыл stream до завершения ответа"),
                    shouldFailover = false,
                )
            }

            emit(
                AssistantStreamEvent.Completed(
                    draft = state.buildDraft(
                        latencyMs = System.currentTimeMillis() - startTime,
                    ),
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun requireApiKeys(): List<String> {
        val apiKeys = settingsRepository.getApiKeys().ifEmpty {
            listOfNotNull(settingsRepository.getPrimaryApiKey())
        }
        if (apiKeys.isEmpty()) {
            throw UiException(UiError.MissingApiKey)
        }
        return apiKeys
    }

    private fun buildRequest(
        authorization: String,
        payload: OpenRouterChatRequest,
    ): Request {
        val body = json.encodeToString(payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(baseUrl)
            .header("Authorization", authorization)
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body)
            .build()
    }
}

internal fun buildOpenRouterMessages(
    history: List<Message>,
    prompt: String,
): List<OpenRouterMessageDto> {
    val trimmedPrompt = prompt.trim()
    val messages = history
        .filter { it.content.isNotBlank() }
        .map { message ->
            OpenRouterMessageDto(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                content = message.content,
            )
        }
        .toMutableList()

    val latestUserPrompt = history
        .asReversed()
        .firstOrNull { it.role == MessageRole.USER }
        ?.content
        ?.trim()

    if (trimmedPrompt.isNotBlank() && latestUserPrompt != trimmedPrompt) {
        messages += OpenRouterMessageDto(role = "user", content = trimmedPrompt)
    }

    return messages
}

private class StreamingAccumulator(
    private val modelId: String,
) {
    private val content = StringBuilder()
    private var usage: OpenRouterUsageDto? = null
    private var finalModel: String? = null
    var isDone: Boolean = false
        private set

    fun consumeEventLines(
        lines: List<String>,
        json: Json,
    ): AssistantStreamEvent? {
        val dataLines = mutableListOf<String>()

        lines.forEach { line ->
            when {
                line.startsWith(":") -> {
                    return AssistantStreamEvent.Streaming(
                        accumulatedContent = content.toString(),
                        isProcessing = content.isEmpty(),
                    )
                }

                line.startsWith("data:") -> {
                    dataLines += line.removePrefix("data:").trimStart()
                }
            }
        }

        if (dataLines.isEmpty()) return null
        val payload = dataLines.joinToString("\n")
        if (payload == "[DONE]") {
            isDone = true
            return null
        }

        val chunk = runCatching {
            json.decodeFromString(OpenRouterStreamChunkDto.serializer(), payload)
        }.getOrElse { throwable ->
            throw OpenRouterExecutionException(
                uiError = UiError.Unknown(throwable.message ?: "Не удалось разобрать streaming-ответ"),
                shouldFailover = false,
                cause = throwable,
            )
        }

        finalModel = chunk.model ?: finalModel ?: modelId
        usage = chunk.usage ?: usage

        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
            throw OpenRouterExecutionException(
                uiError = chunk.error.toStreamUiError(message, modelId),
                shouldFailover = false,
            )
        }

        val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
        if (delta.isNotEmpty()) {
            content.append(delta)
            return AssistantStreamEvent.Streaming(
                accumulatedContent = content.toString(),
                isProcessing = false,
            )
        }

        val finishReason = chunk.choices.firstOrNull()?.finishReason
        if (finishReason != null && finishReason != "error") {
            isDone = true
        }

        return AssistantStreamEvent.Streaming(
            accumulatedContent = content.toString(),
            isProcessing = content.isEmpty(),
        )
    }

    fun buildDraft(latencyMs: Long): AssistantMessageDraft {
        val finalContent = content.toString().trim()
        if (finalContent.isBlank()) {
            throw OpenRouterExecutionException(
                uiError = UiError.Provider("Провайдер вернул пустой ответ"),
                shouldFailover = false,
            )
        }
        return AssistantMessageDraft(
            content = finalContent,
            provider = ModelProvider.OPEN_ROUTER,
            model = finalModel ?: modelId,
            latencyMs = latencyMs,
            estimatedCost = usage.toEstimatedCost(),
        )
    }
}

private class OpenRouterExecutionException(
    val uiError: UiError,
    val shouldFailover: Boolean,
    cause: Throwable? = null,
) : RuntimeException(
    when (uiError) {
        UiError.None -> "Unknown OpenRouter failure"
        UiError.MissingApiKey -> "Missing OpenRouter API key"
        is UiError.Network -> uiError.message
        is UiError.Provider -> uiError.message
        is UiError.Unknown -> uiError.message
        is UiError.Validation -> uiError.message
    },
    cause,
) {
    fun toUiException(): UiException = UiException(uiError, this)
}

private suspend fun SettingsRepository.promoteSuccessfulFallback(index: Int, apiKey: String) {
    if (index == 0) return
    runCatching {
        addApiKey(apiKey)
    }
}

private fun Response.toExecutionException(
    json: Json,
    modelId: String? = null,
): OpenRouterExecutionException {
    val code = code
    val rawBody = runCatching { body?.string().orEmpty() }.getOrDefault("")
    val providerMessage = rawBody.extractProviderMessage(json)

    return when (code) {
        400, 422 -> OpenRouterExecutionException(
            uiError = UiError.Provider(providerMessage ?: "Провайдер вернул ошибку $code"),
            shouldFailover = false,
        )

        401 -> OpenRouterExecutionException(
            uiError = UiError.MissingApiKey,
            shouldFailover = true,
        )

        402 -> OpenRouterExecutionException(
            uiError = UiError.Provider(providerMessage ?: "У текущего OpenRouter API key закончились credits"),
            shouldFailover = true,
        )

        403 -> OpenRouterExecutionException(
            uiError = UiError.Provider(providerMessage ?: "Провайдер отклонил запрос"),
            shouldFailover = false,
        )

        429 -> OpenRouterExecutionException(
            uiError = UiError.Network(rateLimitMessage(providerMessage, modelId, code)),
            shouldFailover = false,
        )

        408, 500, 502, 503 -> OpenRouterExecutionException(
            uiError = UiError.Network(providerMessage ?: "Сервис OpenRouter временно недоступен ($code)"),
            shouldFailover = false,
        )

        else -> OpenRouterExecutionException(
            uiError = UiError.Provider(providerMessage ?: "Провайдер вернул ошибку $code"),
            shouldFailover = false,
        )
    }
}

private fun Throwable.toModelsExecutionException(): OpenRouterExecutionException =
    when (val ui = mapNetworkFailure(this).error) {
        UiError.MissingApiKey -> OpenRouterExecutionException(ui, shouldFailover = true, cause = this)
        is UiError.Network -> OpenRouterExecutionException(
            ui,
            shouldFailover = false,
            cause = this,
        )
        is UiError.Provider -> OpenRouterExecutionException(
            ui,
            shouldFailover = ui.message.contains("credits", ignoreCase = true),
            cause = this,
        )
        is UiError.Unknown -> OpenRouterExecutionException(ui, shouldFailover = false, cause = this)
        is UiError.Validation -> OpenRouterExecutionException(ui, shouldFailover = false, cause = this)
        UiError.None -> OpenRouterExecutionException(UiError.Unknown("Unknown error"), shouldFailover = false, cause = this)
    }

private fun String.extractProviderMessage(json: Json): String? =
    takeIf { it.isNotBlank() }
        ?.let {
            runCatching { json.decodeFromString(OpenRouterErrorResponse.serializer(), it) }
                .getOrNull()
                ?.error
                ?.message
                ?.trim()
        }
        ?.takeIf { it.isNotBlank() }

private fun com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterErrorDto.toStreamUiError(
    fallbackMessage: String,
    modelId: String? = null,
): UiError {
    val normalizedCode = code.orEmpty().lowercase()
    return when {
        "rate" in normalizedCode ->
            UiError.Network(rateLimitMessage(message ?: fallbackMessage, modelId, null))

        "timeout" in normalizedCode || "server" in normalizedCode || "provider" in normalizedCode ->
            UiError.Network(message ?: fallbackMessage)

        else -> UiError.Provider(message ?: fallbackMessage)
    }
}

private fun rateLimitMessage(
    providerMessage: String?,
    modelId: String?,
    code: Int?,
): String {
    val baseMessage = providerMessage?.takeIf { it.isNotBlank() }
        ?: "Сервис OpenRouter временно недоступен (${code ?: 429})"
    return if (modelId.isFreeVariant()) {
        "$baseMessage. Выбрана free-модель: у OpenRouter для :free действуют более жёсткие лимиты, включая 20 запросов в минуту и низкий дневной лимит. Попробуй позже, выбери модель без :free или добавь credits."
    } else {
        baseMessage
    }
}

private fun String?.isFreeVariant(): Boolean = this?.endsWith(":free", ignoreCase = true) == true

private fun String.toBearerToken(): String = "Bearer $this"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val OPEN_ROUTER_CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
private const val MODELS_CACHE_FRESHNESS_MS = 6 * 60 * 60 * 1000L

private fun CachedModelEntity.toDomainModel(): ModelCatalogEntry = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.valueOf(provider),
    model = model,
    label = label,
    supportsChat = supportsChat,
    supportsCompare = supportsCompare,
)

fun createOpenRouterChatRepository(
    settingsRepository: SettingsRepository,
): ChatRepository = OpenRouterChatRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
)

fun createOpenRouterCompareRepository(
    settingsRepository: SettingsRepository,
): CompareRepository = OpenRouterCompareRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
)

fun createOpenRouterDiagnosticsRepository(
    service: OpenRouterService,
    settingsRepository: SettingsRepository,
): OpenRouterDiagnosticsRepository = DefaultOpenRouterDiagnosticsRepository(
    service = service,
    settingsRepository = settingsRepository,
)
