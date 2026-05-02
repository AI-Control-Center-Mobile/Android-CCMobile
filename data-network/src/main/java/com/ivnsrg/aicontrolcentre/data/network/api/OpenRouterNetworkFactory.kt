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

    fun createOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun createJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    fun createService(client: OkHttpClient = createOkHttpClient()): OpenRouterService =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(createJson().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRouterService::class.java)
}
