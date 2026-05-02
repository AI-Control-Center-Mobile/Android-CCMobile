package com.ivnsrg.aicontrolcentre.data.network.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object OpenRouterNetworkFactory {
    private const val BASE_URL = "https://openrouter.ai/api/v1/"
    private const val REQUEST_TIMEOUT_SECONDS = 60L

    fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun createJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    fun createOpenRouterService(client: OkHttpClient = createOkHttpClient()): OpenRouterService {
        val json = createJson()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRouterService::class.java)
    }

    fun createOpenAiCompatibleService(
        baseUrl: String,
        client: OkHttpClient = createOkHttpClient(),
    ): OpenAiCompatibleService {
        val json = createJson()
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiCompatibleService::class.java)
    }

    fun createSiliconFlowService(
        client: OkHttpClient = createOkHttpClient(),
    ): SiliconFlowService {
        val json = createJson()
        return Retrofit.Builder()
            .baseUrl("https://api.siliconflow.com/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SiliconFlowService::class.java)
    }
}

private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"
