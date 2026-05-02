package com.ivnsrg.aicontrolcentre.data.network.repository

import com.ivnsrg.aicontrolcentre.core.model.AssistantMessageDraft
import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.ChatRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareRepository
import com.ivnsrg.aicontrolcentre.core.model.CompareResultPayload
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.ModelCatalogEntry
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.ModelsRepository
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterDiagnosticsRepository
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterKeyDiagnostics
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaRepository
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSnapshot
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaSource
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaStatus
import com.ivnsrg.aicontrolcentre.core.model.ProviderQuotaValue
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.core.model.toReadableMessage
import com.ivnsrg.aicontrolcentre.core.model.toUiError
import com.ivnsrg.aicontrolcentre.data.network.api.OpenAiCompatibleService
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.api.SiliconFlowService
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleChatRequest
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleErrorDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleStreamChunkDto
import com.ivnsrg.aicontrolcentre.data.network.mapper.buildOpenAiCompatibleMessages
import com.ivnsrg.aicontrolcentre.data.network.mapper.extractProviderMessage
import com.ivnsrg.aicontrolcentre.data.network.mapper.mapNetworkFailure
import com.ivnsrg.aicontrolcentre.data.network.mapper.toAssistantMessageDraft
import com.ivnsrg.aicontrolcentre.data.network.mapper.toDomain
import com.ivnsrg.aicontrolcentre.data.network.mapper.toDomain as toOpenRouterDiagnostics
import com.ivnsrg.aicontrolcentre.data.network.mapper.toEstimatedCost
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import com.ivnsrg.aicontrolcentre.data.storage.preferences.AppPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.system.measureTimeMillis

data class ProviderGatewayConfig(
    val provider: ModelProvider,
    val baseUrl: String,
    val modelsService: OpenAiCompatibleService,
)

class UnifiedModelsRepository(
    private val modelsDao: ModelsDao,
    private val settingsRepository: SettingsRepository,
    gatewayConfigs: List<ProviderGatewayConfig>,
) : ModelsRepository {
    private val gateways = gatewayConfigs.associateBy { it.provider }

    override suspend fun getCachedModels(): List<ModelCatalogEntry> {
        val configuredProviders = settingsRepository.getProviderKeys().mapTo(linkedSetOf()) { it.provider }
        return modelsDao.getAll()
            .filter { configuredProviders.isEmpty() || ModelProvider.valueOf(it.provider) in configuredProviders }
            .map(CachedModelEntity::toDomainModel)
            .sortedWith(compareBy<ModelCatalogEntry> { it.provider.displayName }.thenBy { it.label.lowercase() })
    }

    override suspend fun refreshModels(forceRefresh: Boolean): List<ModelCatalogEntry> {
        val providerKeys = settingsRepository.getProviderKeys()
        if (providerKeys.isEmpty()) {
            throw UiException(UiError.MissingApiKey)
        }

        if (!forceRefresh) {
            getFreshCachedModels(providerKeys.mapTo(linkedSetOf()) { it.provider })?.let { return it }
        }

        val cachedByProvider = modelsDao.getAll().groupBy { ModelProvider.valueOf(it.provider) }
        val merged = mutableListOf<ModelCatalogEntry>()
        var lastFailure: Throwable? = null

        providerKeys.forEach { providerKey ->
            val gateway = gateways[providerKey.provider]
            if (gateway == null) {
                lastFailure = UiException(UiError.Provider("Provider is not configured"))
                return@forEach
            }

            runCatching {
                gateway.modelsService.getModels(providerKey.key.toBearerToken())
                    .data
                    .map { it.toDomain(providerKey.provider) }
            }.onSuccess { models ->
                merged += models
            }.onFailure { throwable ->
                lastFailure = throwable
                merged += cachedByProvider[providerKey.provider].orEmpty().map(CachedModelEntity::toDomainModel)
            }
        }

        if (merged.isEmpty()) {
            throw mapNetworkFailure(lastFailure ?: IOException("No provider models available"))
        }

        modelsDao.clear()
        modelsDao.insertAll(
            merged.map {
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

        return merged
            .distinctBy { it.id }
            .sortedWith(compareBy<ModelCatalogEntry> { it.provider.displayName }.thenBy { it.label.lowercase() })
    }

    private suspend fun getFreshCachedModels(
        configuredProviders: Set<ModelProvider>,
    ): List<ModelCatalogEntry>? {
        val cached = modelsDao.getAll().filter { ModelProvider.valueOf(it.provider) in configuredProviders }
        if (cached.isEmpty()) return null
        if (!configuredProviders.all { provider -> cached.any { it.provider == provider.name } }) return null
        if (cached.any { System.currentTimeMillis() - it.cachedAt > MODELS_CACHE_FRESHNESS_MS }) return null
        return cached
            .map(CachedModelEntity::toDomainModel)
            .sortedWith(compareBy<ModelCatalogEntry> { it.provider.displayName }.thenBy { it.label.lowercase() })
    }
}

class DefaultOpenRouterDiagnosticsRepository(
    private val service: OpenRouterService,
    private val settingsRepository: SettingsRepository,
) : OpenRouterDiagnosticsRepository {
    override suspend fun getCurrentKeyDiagnostics(): OpenRouterKeyDiagnostics {
        val apiKey = settingsRepository.getApiKey(ModelProvider.OPEN_ROUTER)
            ?: throw UiException(UiError.MissingApiKey)
        val response = service.getCurrentKeyInfo(apiKey.toBearerToken())
        return response.data?.toOpenRouterDiagnostics()
            ?: throw UiException(UiError.Provider("OpenRouter did not return current key diagnostics"))
    }
}

class DefaultProviderQuotaRepository(
    private val settingsRepository: SettingsRepository,
    private val openRouterDiagnosticsRepository: OpenRouterDiagnosticsRepository,
    private val siliconFlowService: SiliconFlowService,
    private val appPreferencesStore: AppPreferencesStore,
) : ProviderQuotaRepository {
    override suspend fun getQuotaSnapshots(): List<ProviderQuotaSnapshot> = buildSnapshots(refreshRemote = false)

    override suspend fun refreshQuotaSnapshots(): List<ProviderQuotaSnapshot> = buildSnapshots(refreshRemote = true)

    override suspend fun recordRateLimitSnapshot(
        provider: ModelProvider,
        remainingRequests: String?,
        remainingTokens: String?,
        resetRequests: String?,
        resetTokens: String?,
    ) {
        if (provider != ModelProvider.GROQ) return
        appPreferencesStore.saveGroqRateLimitSnapshot(
            remainingRequests = remainingRequests,
            remainingTokens = remainingTokens,
            resetRequests = resetRequests,
            resetTokens = resetTokens,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun buildSnapshots(refreshRemote: Boolean): List<ProviderQuotaSnapshot> {
        val configuredProviders = settingsRepository.getProviderKeys().mapTo(linkedSetOf()) { it.provider }
        return configuredProviders.map { provider ->
            when (provider) {
                ModelProvider.OPEN_ROUTER -> loadOpenRouterSnapshot()
                ModelProvider.SILICON_FLOW -> loadSiliconFlowSnapshot()
                ModelProvider.GROQ -> loadGroqSnapshot()
            }
        }
    }

    private suspend fun loadOpenRouterSnapshot(): ProviderQuotaSnapshot =
        runCatching { openRouterDiagnosticsRepository.getCurrentKeyDiagnostics() }
            .map { diagnostics ->
                ProviderQuotaSnapshot(
                    provider = ModelProvider.OPEN_ROUTER,
                    status = ProviderQuotaStatus.AVAILABLE,
                    headline = if (diagnostics.isFreeTier) "OpenRouter free-tier limits" else "OpenRouter usage",
                    values = buildList {
                        diagnostics.limitRemaining?.let {
                            add(ProviderQuotaValue("Remaining", formatQuotaValue(it)))
                        }
                        add(ProviderQuotaValue("Daily usage", formatQuotaValue(diagnostics.usageDaily)))
                        diagnostics.limitReset?.takeIf { it.isNotBlank() }?.let {
                            add(ProviderQuotaValue("Reset", it))
                        }
                    },
                    detail = diagnostics.label?.let { "Key: $it" },
                    updatedAt = System.currentTimeMillis(),
                    source = ProviderQuotaSource.LIVE,
                )
            }.getOrElse { throwable ->
                ProviderQuotaSnapshot(
                    provider = ModelProvider.OPEN_ROUTER,
                    status = ProviderQuotaStatus.ERROR,
                    headline = "OpenRouter diagnostics unavailable",
                    detail = throwable.toUiError().toReadableMessage(),
                    updatedAt = System.currentTimeMillis(),
                    source = ProviderQuotaSource.LIVE,
                )
            }

    private suspend fun loadSiliconFlowSnapshot(): ProviderQuotaSnapshot {
        val apiKey = settingsRepository.getApiKey(ModelProvider.SILICON_FLOW)
            ?: return ProviderQuotaSnapshot(
                provider = ModelProvider.SILICON_FLOW,
                status = ProviderQuotaStatus.UNAVAILABLE,
                headline = "SiliconFlow key missing",
                detail = "Add a provider key to load balance information.",
            )

        return runCatching { siliconFlowService.getUserInfo(apiKey.toBearerToken()) }
            .map { response ->
                val data = response.data
                ProviderQuotaSnapshot(
                    provider = ModelProvider.SILICON_FLOW,
                    status = if (response.status && data != null) ProviderQuotaStatus.AVAILABLE else ProviderQuotaStatus.ERROR,
                    headline = "SiliconFlow balance",
                    values = buildList {
                        data?.balance?.let { add(ProviderQuotaValue("Balance", it)) }
                        data?.chargeBalance?.let { add(ProviderQuotaValue("Charge balance", it)) }
                        data?.totalBalance?.let { add(ProviderQuotaValue("Total balance", it)) }
                    },
                    detail = data?.status ?: response.message,
                    updatedAt = System.currentTimeMillis(),
                    source = ProviderQuotaSource.LIVE,
                )
            }.getOrElse { throwable ->
                ProviderQuotaSnapshot(
                    provider = ModelProvider.SILICON_FLOW,
                    status = ProviderQuotaStatus.ERROR,
                    headline = "SiliconFlow balance unavailable",
                    detail = throwable.toUiError().toReadableMessage(),
                    updatedAt = System.currentTimeMillis(),
                    source = ProviderQuotaSource.LIVE,
                )
            }
    }

    private suspend fun loadGroqSnapshot(): ProviderQuotaSnapshot {
        val snapshot = appPreferencesStore.getGroqRateLimitSnapshot()
        if (snapshot == null) {
            return ProviderQuotaSnapshot(
                provider = ModelProvider.GROQ,
                status = ProviderQuotaStatus.UNAVAILABLE,
                headline = "No Groq quota snapshot yet",
                detail = "Run at least one successful Groq request to capture the latest rate-limit headers.",
                source = ProviderQuotaSource.SNAPSHOT,
            )
        }

        return ProviderQuotaSnapshot(
            provider = ModelProvider.GROQ,
            status = ProviderQuotaStatus.AVAILABLE,
            headline = "Last known Groq rate limits",
            values = buildList {
                snapshot.remainingRequests?.let { add(ProviderQuotaValue("Remaining requests", it)) }
                snapshot.remainingTokens?.let { add(ProviderQuotaValue("Remaining tokens", it)) }
                snapshot.resetRequests?.let { add(ProviderQuotaValue("Reset requests", it)) }
                snapshot.resetTokens?.let { add(ProviderQuotaValue("Reset tokens", it)) }
            },
            detail = "Captured from response headers.",
            updatedAt = snapshot.updatedAt,
            source = ProviderQuotaSource.SNAPSHOT,
        )
    }
}

class UnifiedChatRepository(
    httpClient: OkHttpClient,
    json: Json,
    settingsRepository: SettingsRepository,
    gatewayConfigs: List<ProviderGatewayConfig>,
    quotaRepository: ProviderQuotaRepository? = null,
) : ChatRepository {
    private val clients = gatewayConfigs.associate { config ->
        config.provider to OpenAiCompatibleClient(
            provider = config.provider,
            httpClient = httpClient,
            json = json,
            settingsRepository = settingsRepository,
            baseUrl = config.baseUrl,
            quotaRepository = quotaRepository,
        )
    }

    override suspend fun sendMessage(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft = requireClient(provider).createCompletion(modelId, prompt, history)

    override fun streamMessage(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = requireClient(provider).streamCompletion(modelId, prompt, history)

    private fun requireClient(provider: ModelProvider): OpenAiCompatibleClient =
        clients[provider] ?: throw UiException(UiError.Provider("Provider is not configured"))
}

class UnifiedCompareRepository(
    httpClient: OkHttpClient,
    json: Json,
    settingsRepository: SettingsRepository,
    gatewayConfigs: List<ProviderGatewayConfig>,
    quotaRepository: ProviderQuotaRepository? = null,
) : CompareRepository {
    private val clients = gatewayConfigs.associate { config ->
        config.provider to OpenAiCompatibleClient(
            provider = config.provider,
            httpClient = httpClient,
            json = json,
            settingsRepository = settingsRepository,
            baseUrl = config.baseUrl,
            quotaRepository = quotaRepository,
        )
    }

    override suspend fun compare(
        threadId: Long,
        providerA: ModelProvider,
        modelA: String,
        providerB: ModelProvider,
        modelB: String,
        prompt: String,
        history: List<Message>,
    ): CompareResultPayload = coroutineScope {
        val first = async { requireClient(providerA).createCompletion(modelA, prompt, history) }
        val second = async { requireClient(providerB).createCompletion(modelB, prompt, history) }
        val drafts = awaitAll(first, second)
        CompareResultPayload(first = drafts[0], second = drafts[1])
    }

    override fun streamModelResponse(
        threadId: Long,
        provider: ModelProvider,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = requireClient(provider).streamCompletion(modelId, prompt, history)

    private fun requireClient(provider: ModelProvider): OpenAiCompatibleClient =
        clients[provider] ?: throw UiException(UiError.Provider("Provider is not configured"))
}

private class OpenAiCompatibleClient(
    private val provider: ModelProvider,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val settingsRepository: SettingsRepository,
    baseUrl: String,
    private val quotaRepository: ProviderQuotaRepository? = null,
) {
    private val chatCompletionsUrl = baseUrl.ensureTrailingSlash() + "chat/completions"

    suspend fun createCompletion(
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): AssistantMessageDraft = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val request = buildRequest(
            authorization = apiKey.toBearerToken(),
            payload = OpenAiCompatibleChatRequest(
                model = modelId,
                messages = buildOpenAiCompatibleMessages(history = history, prompt = prompt),
            ),
        )

        var responseModel: OpenAiCompatibleChatResponse? = null
        val latencyMs = measureTimeMillis {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw response.toExecutionException(json, provider, modelId)
                }
                quotaRepository?.recordRateLimitSnapshot(
                    provider = provider,
                    remainingRequests = response.header("x-ratelimit-remaining-requests"),
                    remainingTokens = response.header("x-ratelimit-remaining-tokens"),
                    resetRequests = response.header("x-ratelimit-reset-requests"),
                    resetTokens = response.header("x-ratelimit-reset-tokens"),
                )
                val body = response.body?.string().orEmpty()
                responseModel = json.decodeFromString(OpenAiCompatibleChatResponse.serializer(), body)
            }
        }

        responseModel?.toAssistantMessageDraft(provider, modelId, latencyMs)
            ?: throw ProviderExecutionException(UiError.Provider("Provider returned an empty response"))
    }

    fun streamCompletion(
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        val apiKey = requireApiKey()
        emit(AssistantStreamEvent.Streaming(accumulatedContent = "", isProcessing = true))
        streamCompletionAttempt(apiKey, modelId, prompt, history).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private fun streamCompletionAttempt(
        apiKey: String,
        modelId: String,
        prompt: String,
        history: List<Message>,
    ): Flow<AssistantStreamEvent> = flow {
        val request = buildRequest(
            authorization = apiKey.toBearerToken(),
            payload = OpenAiCompatibleChatRequest(
                model = modelId,
                messages = buildOpenAiCompatibleMessages(history = history, prompt = prompt),
                stream = true,
            ),
        )

        val startTime = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.toExecutionException(json, provider, modelId)
            }
            quotaRepository?.recordRateLimitSnapshot(
                provider = provider,
                remainingRequests = response.header("x-ratelimit-remaining-requests"),
                remainingTokens = response.header("x-ratelimit-remaining-tokens"),
                resetRequests = response.header("x-ratelimit-reset-requests"),
                resetTokens = response.header("x-ratelimit-reset-tokens"),
            )

            val source = response.body?.source()
                ?: throw ProviderExecutionException(UiError.Provider("Provider returned an empty stream"))

            val state = StreamingAccumulator(provider = provider, modelId = modelId)
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
                throw ProviderExecutionException(
                    UiError.Network("${provider.displayName} closed the stream before the response completed"),
                )
            }

            emit(AssistantStreamEvent.Completed(draft = state.buildDraft(System.currentTimeMillis() - startTime)))
        }
    }

    private suspend fun requireApiKey(): String =
        settingsRepository.getApiKey(provider) ?: throw UiException(UiError.MissingApiKey)

    private fun buildRequest(
        authorization: String,
        payload: OpenAiCompatibleChatRequest,
    ): Request {
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(chatCompletionsUrl)
            .header("Authorization", authorization)
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body)
            .build()
    }
}

private class StreamingAccumulator(
    private val provider: ModelProvider,
    private val modelId: String,
) {
    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private var usage: com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleUsageDto? = null
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

                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }

        if (dataLines.isEmpty()) return null
        val payload = dataLines.joinToString("\n")
        if (payload == "[DONE]") {
            isDone = true
            return null
        }

        val chunk = runCatching {
            json.decodeFromString(OpenAiCompatibleStreamChunkDto.serializer(), payload)
        }.getOrElse { throwable ->
            throw ProviderExecutionException(
                uiError = UiError.Unknown(throwable.message ?: "Failed to parse streaming response"),
                cause = throwable,
            )
        }

        finalModel = chunk.model ?: finalModel ?: modelId
        usage = chunk.usage ?: usage

        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
            throw ProviderExecutionException(
                uiError = chunk.error.toStreamUiError(provider, message, modelId),
            )
        }

        val reasoningDelta = chunk.choices.firstOrNull()?.delta?.reasoningContent.orEmpty()
        if (reasoningDelta.isNotEmpty()) {
            reasoning.append(reasoningDelta)
            return AssistantStreamEvent.Streaming(
                accumulatedContent = combinedContent(),
                isProcessing = false,
            )
        }

        val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
        if (delta.isNotEmpty()) {
            content.append(delta)
            return AssistantStreamEvent.Streaming(
                accumulatedContent = combinedContent(),
                isProcessing = false,
            )
        }

        val finishReason = chunk.choices.firstOrNull()?.finishReason
        if (finishReason != null && finishReason != "error") {
            isDone = true
        }

        return AssistantStreamEvent.Streaming(
            accumulatedContent = combinedContent(),
            isProcessing = content.isEmpty(),
        )
    }

    fun buildDraft(latencyMs: Long): AssistantMessageDraft {
        val finalContent = combinedContent().trim()
        if (finalContent.isBlank()) {
            throw ProviderExecutionException(UiError.Provider("Provider returned an empty response"))
        }
        return AssistantMessageDraft(
            content = finalContent,
            provider = provider,
            model = finalModel ?: modelId,
            latencyMs = latencyMs,
            estimatedCost = usage.toEstimatedCost(),
        )
    }

    private fun combinedContent(): String {
        val reasoningText = reasoning.toString().trim()
        val answerText = content.toString()
        return when {
            reasoningText.isBlank() -> answerText
            answerText.isBlank() -> "<think>$reasoningText</think>"
            else -> "<think>$reasoningText</think>\n\n$answerText"
        }
    }
}

private class ProviderExecutionException(
    val uiError: UiError,
    cause: Throwable? = null,
) : RuntimeException(
    when (uiError) {
        UiError.None -> "Unknown provider failure"
        UiError.MissingApiKey -> "Missing provider API key"
        is UiError.Network -> uiError.message
        is UiError.Provider -> uiError.message
        is UiError.Unknown -> uiError.message
        is UiError.Validation -> uiError.message
    },
    cause,
) {
    fun toUiException(): UiException = UiException(uiError, this)
}

private fun Response.toExecutionException(
    json: Json,
    provider: ModelProvider,
    modelId: String? = null,
): ProviderExecutionException {
    val code = code
    val rawBody = runCatching { body?.string().orEmpty() }.getOrDefault("")
    val providerMessage = rawBody.extractProviderMessage(json)

    return when (code) {
        400, 422 -> ProviderExecutionException(
            uiError = UiError.Provider(providerMessage ?: "${provider.displayName} rejected the request ($code)"),
        )

        401, 403 -> ProviderExecutionException(
            uiError = UiError.MissingApiKey,
        )

        429 -> ProviderExecutionException(
            uiError = UiError.Network(rateLimitMessage(provider, providerMessage, modelId, code)),
        )

        408, 500, 502, 503 -> ProviderExecutionException(
            uiError = UiError.Network(providerMessage ?: "${provider.displayName} is temporarily unavailable ($code)"),
        )

        else -> ProviderExecutionException(
            uiError = UiError.Provider(providerMessage ?: "${provider.displayName} returned an error ($code)"),
        )
    }
}

private fun Throwable.toProviderExecutionException(): ProviderExecutionException =
    when (val ui = mapNetworkFailure(this).error) {
        UiError.MissingApiKey -> ProviderExecutionException(ui, cause = this)
        is UiError.Network -> ProviderExecutionException(ui, cause = this)
        is UiError.Provider -> ProviderExecutionException(ui, cause = this)
        is UiError.Unknown -> ProviderExecutionException(ui, cause = this)
        is UiError.Validation -> ProviderExecutionException(ui, cause = this)
        UiError.None -> ProviderExecutionException(UiError.Unknown("Unknown error"), cause = this)
    }

private fun OpenAiCompatibleErrorDto.toStreamUiError(
    provider: ModelProvider,
    fallbackMessage: String,
    modelId: String? = null,
): UiError {
    val normalizedCode = code.orEmpty().lowercase()
    return when {
        "rate" in normalizedCode ->
            UiError.Network(rateLimitMessage(provider, message ?: fallbackMessage, modelId, null))

        "timeout" in normalizedCode || "server" in normalizedCode || "provider" in normalizedCode ->
            UiError.Network(message ?: fallbackMessage)

        else -> UiError.Provider(message ?: fallbackMessage)
    }
}

private fun rateLimitMessage(
    provider: ModelProvider,
    providerMessage: String?,
    modelId: String?,
    code: Int?,
): String {
    val baseMessage = providerMessage?.takeIf { it.isNotBlank() }
        ?: "${provider.displayName} is temporarily unavailable (${code ?: 429})"
    return if (provider == ModelProvider.OPEN_ROUTER && modelId.isFreeVariant()) {
        "$baseMessage. The selected model is a free variant, which has stricter OpenRouter rate limits."
    } else {
        baseMessage
    }
}

private fun String?.isFreeVariant(): Boolean = this?.endsWith(":free", ignoreCase = true) == true

private fun String.toBearerToken(): String = "Bearer $this"

private fun CachedModelEntity.toDomainModel(): ModelCatalogEntry = ModelCatalogEntry(
    id = id,
    provider = ModelProvider.valueOf(provider),
    model = model,
    label = label,
    supportsChat = supportsChat,
    supportsCompare = supportsCompare,
)

private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"

private fun formatQuotaValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.2f", value)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val MODELS_CACHE_FRESHNESS_MS = 6 * 60 * 60 * 1000L
private const val OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/"
private const val SILICON_FLOW_BASE_URL = "https://api.siliconflow.com/v1/"

fun createOpenRouterDiagnosticsRepository(
    service: OpenRouterService,
    settingsRepository: SettingsRepository,
): OpenRouterDiagnosticsRepository = DefaultOpenRouterDiagnosticsRepository(
    service = service,
    settingsRepository = settingsRepository,
)

fun createOpenRouterDiagnosticsRepository(
    settingsRepository: SettingsRepository,
): OpenRouterDiagnosticsRepository = DefaultOpenRouterDiagnosticsRepository(
    service = OpenRouterNetworkFactory.createOpenRouterService(),
    settingsRepository = settingsRepository,
)

fun createUnifiedModelsRepository(
    modelsDao: ModelsDao,
    settingsRepository: SettingsRepository,
): ModelsRepository = UnifiedModelsRepository(
    modelsDao = modelsDao,
    settingsRepository = settingsRepository,
    gatewayConfigs = defaultProviderGatewayConfigs(),
)

fun createUnifiedChatRepository(
    settingsRepository: SettingsRepository,
    providerQuotaRepository: ProviderQuotaRepository? = null,
): ChatRepository = UnifiedChatRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    gatewayConfigs = defaultProviderGatewayConfigs(),
    quotaRepository = providerQuotaRepository,
)

fun createUnifiedCompareRepository(
    settingsRepository: SettingsRepository,
    providerQuotaRepository: ProviderQuotaRepository? = null,
): CompareRepository = UnifiedCompareRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    gatewayConfigs = defaultProviderGatewayConfigs(),
    quotaRepository = providerQuotaRepository,
)

fun createProviderQuotaRepository(
    settingsRepository: SettingsRepository,
    appPreferencesStore: AppPreferencesStore,
    openRouterDiagnosticsRepository: OpenRouterDiagnosticsRepository,
): ProviderQuotaRepository = DefaultProviderQuotaRepository(
    settingsRepository = settingsRepository,
    openRouterDiagnosticsRepository = openRouterDiagnosticsRepository,
    siliconFlowService = OpenRouterNetworkFactory.createSiliconFlowService(),
    appPreferencesStore = appPreferencesStore,
)

private fun defaultProviderGatewayConfigs(): List<ProviderGatewayConfig> {
    val client = OpenRouterNetworkFactory.createOkHttpClient()
    return listOf(
        ProviderGatewayConfig(
            provider = ModelProvider.OPEN_ROUTER,
            baseUrl = OPEN_ROUTER_BASE_URL,
            modelsService = OpenRouterNetworkFactory.createOpenAiCompatibleService(
                baseUrl = OPEN_ROUTER_BASE_URL,
                client = client,
            ),
        ),
        ProviderGatewayConfig(
            provider = ModelProvider.GROQ,
            baseUrl = GROQ_BASE_URL,
            modelsService = OpenRouterNetworkFactory.createOpenAiCompatibleService(
                baseUrl = GROQ_BASE_URL,
                client = client,
            ),
        ),
        ProviderGatewayConfig(
            provider = ModelProvider.SILICON_FLOW,
            baseUrl = SILICON_FLOW_BASE_URL,
            modelsService = OpenRouterNetworkFactory.createOpenAiCompatibleService(
                baseUrl = SILICON_FLOW_BASE_URL,
                client = client,
            ),
        ),
    )
}
