package com.ivnsrg.aicontrolcentre.data.network.mapper

import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChoiceDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterChatResponse
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterMessageDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterModelDto
import com.ivnsrg.aicontrolcentre.data.network.dto.OpenRouterUsageDto
import org.junit.Assert.assertEquals
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
}
