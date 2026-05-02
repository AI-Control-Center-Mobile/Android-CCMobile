package com.ivnsrg.aicontrolcentre.data.network.api

import com.ivnsrg.aicontrolcentre.data.network.dto.OpenAiCompatibleModelsResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface OpenAiCompatibleService {
    @GET("models")
    suspend fun getModels(
        @Header("Authorization") authorization: String,
    ): OpenAiCompatibleModelsResponse
}
