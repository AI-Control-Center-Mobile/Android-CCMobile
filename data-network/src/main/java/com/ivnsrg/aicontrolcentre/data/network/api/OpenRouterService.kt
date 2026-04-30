package com.ivnsrg.aicontrolcentre.data.network.api

import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatRequest
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterKeyInfoResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterService {
    @GET("key")
    suspend fun getCurrentKeyInfo(
        @Header("Authorization") authorization: String,
    ): OpenRouterKeyInfoResponse

    @GET("models")
    suspend fun getModels(
        @Header("Authorization") authorization: String,
    ): OpenRouterModelsResponse

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenRouterChatRequest,
    ): OpenRouterChatResponse
}
