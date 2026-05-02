package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.OpenRouterKeyDiagnostics
import com.ivnsrg.aicontrolcentre.core.model.ProviderApiKey
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.data.network.api.OpenAiCompatibleService
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleChoiceDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleMessageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleModelDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleModelsResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleUsageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterKeyInfoDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterKeyInfoResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelsResponse
import com.ivnsrg.aicontrolcentre.data.network.repository.DefaultOpenRouterDiagnosticsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.ProviderGatewayConfig
import com.ivnsrg.aicontrolcentre.data.network.repository.UnifiedChatRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.UnifiedCompareRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.UnifiedModelsRepository
import com.ivnsrg.aicontrolcentre.data.network.mapper.buildOpenAiCompatibleMessages
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkMappersTest {

    @Test
    fun `model dto maps to domain model`() {
        val dto = OpenAiCompatibleModelDto(
            id = "openrouter/auto",
            name = "Auto",
        )

        val result = dto.toDomain(ModelProvider.OPEN_ROUTER)

        assertEquals("OPEN_ROUTER:openrouter/auto", result.id)
        assertEquals("Auto", result.label)
    }

    @Test
    fun `chat response maps to assistant draft`() {
        val response = OpenAiCompatibleChatResponse(
            choices = listOf(
                OpenAiCompatibleChoiceDto(
                    message = OpenAiCompatibleMessageDto(
                        role = "assistant",
                        content = "Hello",
                    ),
                ),
            ),
            usage = OpenAiCompatibleUsageDto(totalCost = 0.012),
        )

        val result = response.toAssistantMessageDraft(
            provider = ModelProvider.OPEN_ROUTER,
            requestedModel = "openrouter/auto",
            latencyMs = 1200L,
        )

        assertEquals("Hello", result.content)
        assertEquals("openrouter/auto", result.model)
        assertEquals(1200L, result.latencyMs)
        assertEquals(0.012, result.estimatedCost ?: 0.0, 0.0)
    }

    @Test
    fun `chat response falls back to local cost estimate from tokens`() {
        val response = OpenAiCompatibleChatResponse(
            choices = listOf(
                OpenAiCompatibleChoiceDto(
                    message = OpenAiCompatibleMessageDto(
                        role = "assistant",
                        content = "Estimated",
                    ),
                ),
            ),
            usage = OpenAiCompatibleUsageDto(
                promptTokens = 200,
                completionTokens = 50,
            ),
        )

        val result = response.toAssistantMessageDraft(
            provider = ModelProvider.OPEN_ROUTER,
            requestedModel = "openrouter/auto",
            latencyMs = 800L,
        )

        assertEquals(0.00025, result.estimatedCost ?: 0.0, 0.0)
    }

    @Test
    fun `empty provider message throws ui exception`() {
        val response = OpenAiCompatibleChatResponse(
            choices = listOf(OpenAiCompatibleChoiceDto(message = OpenAiCompatibleMessageDto(role = "assistant", content = ""))),
        )

        try {
            response.toAssistantMessageDraft(
                provider = ModelProvider.OPEN_ROUTER,
                requestedModel = "openrouter/auto",
                latencyMs = 400L,
            )
            fail("Expected UiException")
        } catch (exception: UiException) {
            assertTrue(exception.error is UiError.Provider)
        }
    }

    @Test
    fun `chat repository requires api key`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val repository = createChatRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = emptyList()),
                baseUrl = server.url("/chat/completions").toString(),
            )

            try {
                repository.sendMessage(
                    threadId = 1L,
                    provider = ModelProvider.OPEN_ROUTER,
                    modelId = "openrouter/auto",
                    prompt = "Hi",
                    history = emptyList(),
                )
                fail("Expected UiException")
            } catch (exception: UiException) {
                assertEquals(UiError.MissingApiKey, exception.error)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chat repository returns missing key style error on unauthorized`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer bad-key" -> jsonError(401, "Invalid key")
                "Bearer good-key" -> jsonSuccess(model = "openrouter/auto", content = "Recovered")
                else -> jsonError(500, "Unexpected auth")
            }
        }
        server.start()

        try {
            val repository = createChatRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("bad-key", "good-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            try {
                repository.sendMessage(
                    threadId = 1L,
                    provider = ModelProvider.OPEN_ROUTER,
                    modelId = "openrouter/auto",
                    prompt = "Hi",
                    history = emptyList(),
                )
                fail("Expected provider exception")
            } catch (exception: Throwable) {
                assertTrue(exception.message?.contains("Missing provider API key") == true)
            }

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chat repository does not fail over on invalid request`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer first-key" -> jsonError(400, "Bad prompt")
                else -> jsonSuccess(model = "openrouter/auto", content = "Should not be called")
            }
        }
        server.start()

        try {
            val repository = createChatRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("first-key", "second-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            try {
                repository.sendMessage(
                    threadId = 1L,
                    provider = ModelProvider.OPEN_ROUTER,
                    modelId = "openrouter/auto",
                    prompt = "Hi",
                    history = emptyList(),
                )
                fail("Expected UiException")
            } catch (exception: Throwable) {
                assertTrue(exception.message?.contains("Bad prompt") == true)
            }

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chat repository includes free tier hint on rate limit for free model`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = jsonError(429, "Rate limit exceeded")
        }
        server.start()

        try {
            val repository = createChatRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            try {
                repository.sendMessage(
                    threadId = 1L,
                    provider = ModelProvider.OPEN_ROUTER,
                    modelId = "google/gemma-3n:free",
                    prompt = "Hi",
                    history = emptyList(),
                )
                fail("Expected UiException")
            } catch (exception: Throwable) {
                assertTrue(exception.message?.contains("free variant") == true)
                assertTrue(exception.message?.contains("Rate limit exceeded") == true)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chat repository streams response and completes`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = streamSuccess(
                model = "anthropic/claude-3.5-sonnet",
                content = "Streaming answer",
            )
        }
        server.start()

        try {
            val repository = createChatRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            val events = repository.streamMessage(
                threadId = 1L,
                provider = ModelProvider.OPEN_ROUTER,
                modelId = "anthropic/claude-3.5-sonnet",
                prompt = "Hi",
                history = emptyList(),
            ).toList()

            assertTrue(events.first() is AssistantStreamEvent.Streaming)
            val completed = events.last() as AssistantStreamEvent.Completed
            assertEquals("Streaming answer", completed.draft.content)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `stream does not fail over on mid stream error`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer bad-key" -> streamFailure(model = "anthropic/claude-3.5-sonnet")
                "Bearer good-key" -> streamSuccess(model = "anthropic/claude-3.5-sonnet", content = "Second attempt")
                else -> jsonError(500, "Unexpected auth")
            }
        }
        server.start()

        try {
            val repository = createCompareRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("bad-key", "good-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            try {
                repository.streamModelResponse(
                    threadId = 10L,
                    provider = ModelProvider.OPEN_ROUTER,
                    modelId = "anthropic/claude-3.5-sonnet",
                    prompt = "Compare",
                    history = emptyList(),
                ).toList()
                fail("Expected UiException")
            } catch (exception: Throwable) {
                assertTrue(exception.message?.contains("boom") == true)
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `compare repository returns two independent results`() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    "\"model\":\"anthropic/claude-3.5-sonnet\"" in body ->
                        jsonSuccess(model = "anthropic/claude-3.5-sonnet", content = "Claude")

                    "\"model\":\"openai/gpt-4o\"" in body ->
                        jsonSuccess(model = "openai/gpt-4o", content = "GPT")

                    else -> jsonError(500, "Unknown model")
                }
            }
        }
        server.start()

        try {
            val repository = createCompareRepository(
                settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
                baseUrl = server.url("/chat/completions").toString(),
            )

            val result = repository.compare(
                threadId = 1L,
                providerA = ModelProvider.OPEN_ROUTER,
                modelA = "anthropic/claude-3.5-sonnet",
                providerB = ModelProvider.OPEN_ROUTER,
                modelB = "openai/gpt-4o",
                prompt = "Compare",
                history = emptyList(),
            )

            assertEquals("anthropic/claude-3.5-sonnet", result.first.model)
            assertEquals("openai/gpt-4o", result.second.model)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `models repository retries next configured key after rate limit`() = runBlocking {
        val service = FakeModelsOpenRouterService()
        val repository = UnifiedModelsRepository(
            modelsDao = FakeModelsDao(),
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("first-key", "second-key")),
            gatewayConfigs = listOf(
                ProviderGatewayConfig(
                    provider = ModelProvider.OPEN_ROUTER,
                    baseUrl = "https://openrouter.ai/api/v1/",
                    modelsService = service,
                ),
            ),
        )

        val models = repository.refreshModels()

        assertEquals(1, models.size)
        assertEquals(listOf("Bearer first-key", "Bearer second-key"), service.authorizations)
    }

    @Test
    fun `models repository reuses fresh cache without repeated network call`() = runBlocking {
        val service = SuccessfulModelsOpenRouterService()
        val repository = UnifiedModelsRepository(
            modelsDao = FakeModelsDao(),
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
            gatewayConfigs = listOf(
                ProviderGatewayConfig(
                    provider = ModelProvider.OPEN_ROUTER,
                    baseUrl = "https://openrouter.ai/api/v1/",
                    modelsService = service,
                ),
            ),
        )

        val first = repository.refreshModels()
        val second = repository.refreshModels()

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertEquals(listOf("Bearer good-key"), service.authorizations)
    }

    @Test
    fun `models repository force refresh bypasses fresh cache`() = runBlocking {
        val service = SuccessfulModelsOpenRouterService()
        val repository = UnifiedModelsRepository(
            modelsDao = FakeModelsDao(),
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
            gatewayConfigs = listOf(
                ProviderGatewayConfig(
                    provider = ModelProvider.OPEN_ROUTER,
                    baseUrl = "https://openrouter.ai/api/v1/",
                    modelsService = service,
                ),
            ),
        )

        repository.refreshModels()
        repository.refreshModels(forceRefresh = true)

        assertEquals(listOf("Bearer good-key", "Bearer good-key"), service.authorizations)
    }

    @Test
    fun `diagnostics repository maps current key info`() = runBlocking {
        val service = SuccessfulDiagnosticsOpenRouterService()
        val repository = DefaultOpenRouterDiagnosticsRepository(
            service = service,
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
        )

        val diagnostics = repository.getCurrentKeyDiagnostics()

        assertEquals(
            OpenRouterKeyDiagnostics(
                label = "Primary key",
                isFreeTier = true,
                limitRemaining = 17.0,
                usageDaily = 9.0,
                limitReset = "daily",
            ),
            diagnostics,
        )
    }

    @Test
    fun `models repository serves concurrent refresh callers independently`() = runBlocking {
        val service = SuccessfulModelsOpenRouterService(delayMs = 100)
        val repository = UnifiedModelsRepository(
            modelsDao = FakeModelsDao(),
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("good-key")),
            gatewayConfigs = listOf(
                ProviderGatewayConfig(
                    provider = ModelProvider.OPEN_ROUTER,
                    baseUrl = "https://openrouter.ai/api/v1/",
                    modelsService = service,
                ),
            ),
        )

        coroutineScope {
            val first = async { repository.refreshModels() }
            val second = async { repository.refreshModels() }

            assertEquals(1, first.await().size)
            assertEquals(1, second.await().size)
        }

        assertEquals(listOf("Bearer good-key", "Bearer good-key"), service.authorizations)
    }

    @Test
    fun `openrouter messages include prior thread context and append new prompt`() {
        val messages = buildOpenAiCompatibleMessages(
            history = listOf(
                message(id = 1L, role = MessageRole.USER, content = "First prompt", model = "model-a"),
                message(id = 2L, role = MessageRole.ASSISTANT, content = "First answer", model = "model-a"),
            ),
            prompt = "Second prompt",
        )

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.role })
        assertEquals(listOf("First prompt", "First answer", "Second prompt"), messages.map { it.content })
    }

    @Test
    fun `openrouter messages do not duplicate latest user prompt`() {
        val messages = buildOpenAiCompatibleMessages(
            history = listOf(
                message(id = 1L, role = MessageRole.USER, content = "Compare this", model = "model-a"),
            ),
            prompt = "Compare this",
        )

        assertEquals(1, messages.size)
        assertEquals("Compare this", messages.single().content)
    }
}

private fun message(
    id: Long,
    role: MessageRole,
    content: String,
    model: String?,
): Message = Message(
    id = id,
    threadId = 1L,
    role = role,
    content = content,
    provider = if (role == MessageRole.ASSISTANT) ModelProvider.OPEN_ROUTER else null,
    model = model,
    latencyMs = null,
    estimatedCost = null,
    createdAt = id,
)

private fun createChatRepository(
    settingsRepository: SettingsRepository,
    baseUrl: String,
): UnifiedChatRepository = UnifiedChatRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    gatewayConfigs = listOf(
        ProviderGatewayConfig(
            provider = ModelProvider.OPEN_ROUTER,
            baseUrl = baseUrl,
            modelsService = OpenRouterNetworkFactory.createOpenAiCompatibleService(baseUrl = baseUrl),
        ),
    ),
)

private fun createCompareRepository(
    settingsRepository: SettingsRepository,
    baseUrl: String,
): UnifiedCompareRepository = UnifiedCompareRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    gatewayConfigs = listOf(
        ProviderGatewayConfig(
            provider = ModelProvider.OPEN_ROUTER,
            baseUrl = baseUrl,
            modelsService = OpenRouterNetworkFactory.createOpenAiCompatibleService(baseUrl = baseUrl),
        ),
    ),
)

private class FakeSettingsRepository(
    private val apiKeys: List<String>,
) : SettingsRepository {
    override suspend fun getProviderKeys(): List<ProviderApiKey> =
        apiKeys.map { ProviderApiKey(provider = ModelProvider.OPEN_ROUTER, key = it) }

    override suspend fun getApiKey(provider: ModelProvider): String? =
        if (provider == ModelProvider.OPEN_ROUTER) apiKeys.firstOrNull() else null

    override suspend fun saveApiKey(provider: ModelProvider, key: String) = Unit

    override suspend fun clearApiKey(provider: ModelProvider) = Unit

    override suspend fun clearAllApiKeys() = Unit

    override suspend fun clearAllLocalData() = Unit
}

private class FakeModelsDao : ModelsDao {
    private val items = mutableListOf<CachedModelEntity>()

    override suspend fun getAll(): List<CachedModelEntity> = items.toList()

    override suspend fun insertAll(models: List<CachedModelEntity>) {
        items.clear()
        items.addAll(models)
    }

    override suspend fun clear(): Int {
        val count = items.size
        items.clear()
        return count
    }
}

private class FakeModelsOpenRouterService : OpenAiCompatibleService {
    val authorizations = mutableListOf<String>()

    override suspend fun getModels(authorization: String): OpenAiCompatibleModelsResponse {
        authorizations += authorization
        if (authorization == "Bearer first-key") {
            throw UiException(UiError.Network("Сервис OpenRouter временно недоступен (429)"))
        }
        return OpenAiCompatibleModelsResponse(
            data = listOf(OpenAiCompatibleModelDto(id = "openrouter/auto", name = "Auto")),
        )
    }
}

private class SuccessfulModelsOpenRouterService(
    private val delayMs: Long = 0,
) : OpenAiCompatibleService {
    val authorizations = mutableListOf<String>()

    override suspend fun getModels(authorization: String): OpenAiCompatibleModelsResponse {
        authorizations += authorization
        if (delayMs > 0) {
            delay(delayMs)
        }
        return OpenAiCompatibleModelsResponse(
            data = listOf(OpenAiCompatibleModelDto(id = "openrouter/auto", name = "Auto")),
        )
    }
}

private class SuccessfulDiagnosticsOpenRouterService : OpenRouterService {
    override suspend fun getCurrentKeyInfo(authorization: String): OpenRouterKeyInfoResponse =
        OpenRouterKeyInfoResponse(
            data = OpenRouterKeyInfoDto(
                label = "Primary key",
                isFreeTier = true,
                limitRemaining = 17.0,
                usageDaily = 9.0,
                limitReset = "daily",
            ),
        )

    override suspend fun getModels(authorization: String): OpenRouterModelsResponse {
        throw UnsupportedOperationException()
    }

    override suspend fun createChatCompletion(
        authorization: String,
        request: com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatRequest,
    ): com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse {
        throw UnsupportedOperationException()
    }
}

private fun jsonSuccess(
    model: String,
    content: String,
): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(
        """
        {
          "model": "$model",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "$content"
              }
            }
          ],
          "usage": {
            "cost": 0.001
          }
        }
        """.trimIndent(),
    )

private fun jsonError(
    code: Int,
    message: String,
): MockResponse = MockResponse()
    .setResponseCode(code)
    .setHeader("Content-Type", "application/json")
    .setBody("""{"error":{"code":"$code","message":"$message"}}""")

private fun streamFailure(model: String): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "text/event-stream")
    .setBody(
        """
        data: {"model":"$model","choices":[{"delta":{"content":"bad"},"finish_reason":null}]}

        data: {"model":"$model","error":{"code":"server_error","message":"boom"},"choices":[{"delta":{"content":""},"finish_reason":"error"}]}

        """.trimIndent() + "\n\n",
    )

private fun streamSuccess(
    model: String,
    content: String,
): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "text/event-stream")
    .setBody(
        """
        : OPENROUTER PROCESSING

        data: {"model":"$model","choices":[{"delta":{"content":"$content"},"finish_reason":null}]}

        data: {"model":"$model","choices":[{"delta":{"content":""},"finish_reason":"stop"}],"usage":{"cost":0.001}}

        data: [DONE]

        """.trimIndent(),
    )
