package com.ivnsrg.aicontrolcentre.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMarkdownTest {

    @Test
    fun `parses headings paragraphs and fenced code blocks`() {
        val blocks = parseMarkdownBlocks(
            """
            # Title

            Intro paragraph

            ```swift
            let x = 1
            ```

            ## Next
            trailing text
            """.trimIndent(),
        )

        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is MessageMarkdownBlock.Heading)
        assertTrue(blocks[1] is MessageMarkdownBlock.Paragraph)
        assertTrue(blocks[2] is MessageMarkdownBlock.CodeBlock)
        assertTrue(blocks[3] is MessageMarkdownBlock.Heading)
        assertTrue(blocks[4] is MessageMarkdownBlock.Paragraph)
    }

    @Test
    fun `parses fourth level heading`() {
        val blocks = parseMarkdownBlocks("#### Small heading")

        assertEquals(1, blocks.size)
        assertEquals(MessageMarkdownBlock.Heading(4, "Small heading"), blocks.first())
    }

    @Test
    fun `parses markdown divider`() {
        val blocks = parseMarkdownBlocks(
            """
            first paragraph

            ---

            second paragraph
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MessageMarkdownBlock.Divider)
    }

    @Test
    fun `parses basic markdown table`() {
        val blocks = parseMarkdownBlocks(
            """
            | Name | Value |
            | --- | --- |
            | Latency | 7.3s |
            | Cost | $0.07 |
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        val table = blocks.first() as MessageMarkdownBlock.Table
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(listOf("Latency", "7.3s"), table.rows.first())
        assertEquals(listOf("Cost", "$0.07"), table.rows[1])
    }

    @Test
    fun `formats latency in seconds`() {
        assertEquals("7.3s", formatLatencySeconds(7340))
        assertEquals("12s", formatLatencySeconds(12000))
    }

    @Test
    fun `formats cost rounded to two decimals`() {
        assertEquals("$0.07", formatRoundedCost(0.073))
        assertEquals("$1.20", formatRoundedCost(1.199))
    }
}
