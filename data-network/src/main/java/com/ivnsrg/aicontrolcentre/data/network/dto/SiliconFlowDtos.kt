package com.ivnsrg.aicontrolcentre.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SiliconFlowUserInfoResponse(
    val code: Int,
    val message: String,
    val status: Boolean,
    val data: SiliconFlowUserInfoDto? = null,
)

@Serializable
data class SiliconFlowUserInfoDto(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val balance: String? = null,
    val chargeBalance: String? = null,
    val totalBalance: String? = null,
    val status: String? = null,
)
