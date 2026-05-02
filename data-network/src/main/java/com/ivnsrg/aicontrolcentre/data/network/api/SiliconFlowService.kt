package com.ivnsrg.aicontrolcentre.data.network.api

import com.ivnsrg.aicontrolcentre.data.network.dto.SiliconFlowUserInfoResponse
import retrofit2.http.GET
import retrofit2.http.Header

interface SiliconFlowService {
    @GET("user/info")
    suspend fun getUserInfo(
        @Header("Authorization") authorization: String,
    ): SiliconFlowUserInfoResponse
}
