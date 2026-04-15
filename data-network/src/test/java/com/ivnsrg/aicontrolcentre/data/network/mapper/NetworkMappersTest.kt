package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.core.model.AssistantStreamEvent
import com.ivnsrg.aicontrolcentre.core.model.Message
import com.ivnsrg.aicontrolcentre.core.model.MessageRole
import com.ivnsrg.aicontrolcentre.core.model.ModelProvider
import com.ivnsrg.aicontrolcentre.core.model.SettingsRepository
import com.ivnsrg.aicontrolcentre.core.model.UiError
import com.ivnsrg.aicontrolcentre.core.model.UiException
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterNetworkFactory
import com.ivnsrg.aicontrolcentre.data.network.api.OpenRouterService
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChoiceDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterMessageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelsResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterUsageDto
import com.ivnsrg.aicontrolcentre.data.network.repository.OpenRouterChatRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.OpenRouterCompareRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.OpenRouterModelsRepository
import com.ivnsrg.aicontrolcentre.data.network.repository.buildOpenRouterMessages
import com.ivnsrg.aicontrolcentre.data.storage.dao.ModelsDao
import com.ivnsrg.aicontrolcentre.data.storage.entity.CachedModelEntity
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
        val dto = OpenRouterModelDto(
            id = "openrouter/auto",
            name = "Auto",
        )

        val result = dto.toDomain()

        assertEquals("openrouter/auto", result.id)
        assertEquals("Auto", result.label)
    }

    @Test
    fun `chat response maps to assistant draft`() {
        val response = OpenRouterChatResponse(
            choices = listOf(
                OpenRouterChoiceDto(
                    message = OpenRouterMessageDto(
                        role = "assistant",
                        content = "Hello",
                    ),
                ),
            ),
            usage = OpenRouterUsageDto(totalCost = 0.012),
        )

        val result = response.toAssistantMessageDraft(
            requestedModel = "openrouter/auto",
            latencyMs = 1200,
        )

        assertEquals("Hello", result.content)
        assertEquals("openrouter/auto", result.model)
        assertEquals(1200L, result.latencyMs)
        assertEquals(0.012, result.estimatedCost ?: 0.0, 0.0)
    }

    @Test
    fun `chat response falls back to local cost estimate from tokens`() {
        val response = OpenRouterChatResponse(
            choices = listOf(
                OpenRouterChoiceDto(
                    message = OpenRouterMessageDto(
                        role = "assistant",
                        content = "Estimated",
                    ),
                ),
            ),
            usage = OpenRouterUsageDto(
                promptTokens = 200,
                completionTokens = 50,
            ),
        )

        val result = response.toAssistantMessageDraft(
            requestedModel = "openrouter/auto",
            latencyMs = 800,
        )

        assertEquals(0.00025, result.estimatedCost ?: 0.0, 0.0)
    }

    @Test
    fun `empty provider message throws ui exception`() {
        val response = OpenRouterChatResponse(
            choices = listOf(OpenRouterChoiceDto(message = OpenRouterMessageDto(role = "assistant", content = ""))),
        )

        try {
            response.toAssistantMessageDraft(
                requestedModel = "openrouter/auto",
                latencyMs = 400,
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
                repository.sendMessage(threadId = 1L, modelId = "openrouter/auto", prompt = "Hi", history = emptyList())
                fail("Expected UiException")
            } catch (exception: UiException) {
                assertEquals(UiError.MissingApiKey, exception.error)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `chat repository fails over to second key on unauthorized`() = runBlocking {
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

            val result = repository.sendMessage(
                threadId = 1L,
                modelId = "openrouter/auto",
                prompt = "Hi",
                history = emptyList(),
            )

            assertEquals("Recovered", result.content)
            assertEquals(2, server.requestCount)
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
                repository.sendMessage(threadId = 1L, modelId = "openrouter/auto", prompt = "Hi", history = emptyList())
                fail("Expected UiException")
            } catch (exception: UiException) {
                assertTrue(exception.error is UiError.Provider)
            }

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `stream retries on mid stream error and completes with second key`() = runBlocking {
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

            val events = repository.streamModelResponse(
                threadId = 10L,
                modelId = "anthropic/claude-3.5-sonnet",
                prompt = "Compare",
                history = emptyList(),
            ).toList()

            val completion = events.last() as AssistantStreamEvent.Completed
            assertEquals("Second attempt", completion.draft.content)
            assertEquals(2, server.requestCount)
            assertTrue(events.first() is AssistantStreamEvent.Streaming)
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
                modelA = "anthropic/claude-3.5-sonnet",
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
    fun `models repository fails over to second key on rate limit`() = runBlocking {
        val service = FakeModelsOpenRouterService()
        val repository = OpenRouterModelsRepository(
            modelsDao = FakeModelsDao(),
            service = service,
            settingsRepository = FakeSettingsRepository(apiKeys = listOf("first-key", "second-key")),
        )

        val models = repository.refreshModels()

        assertEquals(1, models.size)
        assertEquals("openrouter/auto", models.first().id)
        assertEquals(listOf("Bearer first-key", "Bearer second-key"), service.authorizations)
    }

    @Test
    fun `openrouter messages include prior thread context and append new prompt`() {
        val messages = buildOpenRouterMessages(
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
        val messages = buildOpenRouterMessages(
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
): OpenRouterChatRepository = OpenRouterChatRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    baseUrl = baseUrl,
)

private fun createCompareRepository(
    settingsRepository: SettingsRepository,
    baseUrl: String,
): OpenRouterCompareRepository = OpenRouterCompareRepository(
    httpClient = OpenRouterNetworkFactory.createOkHttpClient(),
    json = OpenRouterNetworkFactory.createJson(),
    settingsRepository = settingsRepository,
    baseUrl = baseUrl,
)

private class FakeSettingsRepository(
    private val apiKeys: List<String>,
) : SettingsRepository {
    override suspend fun getApiKeys(): List<String> = apiKeys

    override suspend fun getPrimaryApiKey(): String? = apiKeys.firstOrNull()

    override suspend fun addApiKey(key: String) = Unit

    override suspend fun removeApiKey(key: String) = Unit

    override suspend fun getApiKey(): String? = apiKeys.firstOrNull()

    override suspend fun saveApiKey(key: String) = Unit

    override suspend fun clearApiKey() = Unit

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

private class FakeModelsOpenRouterService : OpenRouterService {
    val authorizations = mutableListOf<String>()

    override suspend fun getModels(authorization: String): OpenRouterModelsResponse {
        authorizations += authorization
        if (authorization == "Bearer first-key") {
            throw UiException(UiError.Network("Сервис OpenRouter временно недоступен (429)"))
        }
        return OpenRouterModelsResponse(
            data = listOf(OpenRouterModelDto(id = "openrouter/auto", name = "Auto")),
        )
    }

    override suspend fun createChatCompletion(
        authorization: String,
        request: com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatRequest,
    ): OpenRouterChatResponse {
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
