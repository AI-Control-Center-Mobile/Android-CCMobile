package com.ivnsrg.aicontrolcentre.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun `mask api key keeps prefix and suffix`() {
        assertEquals(
            "sk-or-v1…cdef",
            maskApiKey("sk-or-v1-1234567890abcdef"),
        )
    }

    @Test
    fun `mask api key keeps short values intact`() {
        assertEquals("short-key", maskApiKey("short-key"))
    }
}
