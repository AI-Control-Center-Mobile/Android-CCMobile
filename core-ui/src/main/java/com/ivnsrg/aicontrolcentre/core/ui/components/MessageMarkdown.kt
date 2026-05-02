package com.ivnsrg.aicontrolcentre.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import java.util.Locale

sealed interface MessageMarkdownBlock {
    data class Heading(val level: Int, val text: String) : MessageMarkdownBlock
    data class Paragraph(val text: String) : MessageMarkdownBlock
    data class CodeBlock(val code: String) : MessageMarkdownBlock
    data class Thought(val text: String) : MessageMarkdownBlock
    data object Divider : MessageMarkdownBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
    ) : MessageMarkdownBlock
}

@Composable
fun AssistantMarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val colors = MaterialTheme.appColors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageMarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            3 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelLarge
                        },
                        color = colors.textPrimary,
                    )
                }

                is MessageMarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                }

                is MessageMarkdownBlock.CodeBlock -> {
                    OperationalCard(
                        tone = CardTone.Surface1,
                        padding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    ) {
                        Text(
                            text = block.code,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                        )
                    }
                }

                is MessageMarkdownBlock.Thought -> {
                    ReasoningBlock(text = block.text)
                }

                MessageMarkdownBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(colors.stroke, RoundedCornerShape(100.dp))
                            .padding(vertical = 0.5.dp),
                    )
                }

                is MessageMarkdownBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

@Composable
fun AssistantMarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 4,
) {
    val previewText = remember(content) {
        buildInlineMarkdown(flattenMarkdownBlocksForPreview(parseMarkdownBlocks(content)))
    }
    val colors = MaterialTheme.appColors

    Text(
        text = previewText,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

fun parseMarkdownBlocks(raw: String): List<MessageMarkdownBlock> {
    val normalized = raw.replace("\r\n", "\n")
    val blocks = mutableListOf<MessageMarkdownBlock>()
    var cursor = 0

    THINK_REGEX.findAll(normalized).forEach { match ->
        val before = normalized.substring(cursor, match.range.first)
        if (before.isNotBlank()) {
            blocks += parseStandardMarkdownBlocks(before)
        }

        val thought = match.groupValues[1].trim()
        if (thought.isNotBlank()) {
            blocks += MessageMarkdownBlock.Thought(thought)
        }
        cursor = match.range.last + 1
    }

    val tail = normalized.substring(cursor)
    if (tail.isNotBlank()) {
        blocks += parseStandardMarkdownBlocks(tail)
    }

    return if (blocks.isEmpty()) listOf(MessageMarkdownBlock.Paragraph(stripThinkTags(normalized))) else blocks
}

private fun parseStandardMarkdownBlocks(raw: String): List<MessageMarkdownBlock> {
    val normalized = raw.replace("\r\n", "\n")
    val lines = normalized.split('\n')
    val blocks = mutableListOf<MessageMarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    val table = mutableListOf<String>()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            val text = paragraph.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                blocks += MessageMarkdownBlock.Paragraph(text)
            }
            paragraph.clear()
        }
    }

    fun flushCode() {
        if (code.isNotEmpty()) {
            blocks += MessageMarkdownBlock.CodeBlock(code.joinToString("\n").trimEnd())
            code.clear()
        }
    }

    fun flushTable() {
        if (table.isEmpty()) return
        val parsed = parseMarkdownTable(table)
        if (parsed != null) {
            blocks += parsed
        } else {
            val text = table.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                blocks += MessageMarkdownBlock.Paragraph(text)
            }
        }
        table.clear()
    }

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushTable()
            if (inCode) {
                flushCode()
            } else {
                flushParagraph()
            }
            inCode = !inCode
            return@forEach
        }

        if (inCode) {
            code += rawLine
            return@forEach
        }

        if (trimmed.isTableRow()) {
            flushParagraph()
            table += trimmed
            return@forEach
        } else {
            flushTable()
        }

        HEADING_REGEX.matchEntire(trimmed)?.let { match ->
            flushParagraph()
            val level = match.groupValues[1].length.coerceIn(1, 4)
            val text = match.groupValues[2].trim()
            if (text.isNotEmpty()) {
                blocks += MessageMarkdownBlock.Heading(level, text)
            }
            return@forEach
        }

        if (trimmed.isDivider()) {
            flushParagraph()
            blocks += MessageMarkdownBlock.Divider
            return@forEach
        }

        if (trimmed.isBlank()) {
            flushParagraph()
        } else {
            paragraph += line
        }
    }

    flushTable()
    flushParagraph()
    if (inCode) {
        flushCode()
    }

    return if (blocks.isEmpty()) listOf(MessageMarkdownBlock.Paragraph(normalized)) else blocks
}

fun buildInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val closing = text.indexOf("**", startIndex = index + 2)
                if (closing > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, closing))
                    pop()
                    index = closing + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text[index] == '*' -> {
                val closing = text.indexOf('*', startIndex = index + 1)
                if (closing > index + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, closing))
                    pop()
                    index = closing + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

private fun flattenMarkdownBlocksForPreview(blocks: List<MessageMarkdownBlock>): String =
    blocks.mapNotNull { block ->
        when (block) {
            is MessageMarkdownBlock.Heading -> block.text.takeIf { it.isNotBlank() }
            is MessageMarkdownBlock.Paragraph -> block.text.takeIf { it.isNotBlank() }
            is MessageMarkdownBlock.CodeBlock -> block.code.trim().takeIf { it.isNotBlank() }
            is MessageMarkdownBlock.Thought -> null
            MessageMarkdownBlock.Divider -> null
            is MessageMarkdownBlock.Table -> buildString {
                if (block.header.isNotEmpty()) {
                    append(block.header.joinToString(" | ").trim())
                }
                block.rows.forEach { row ->
                    val rowText = row.joinToString(" | ").trim()
                    if (rowText.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(rowText)
                    }
                }
            }.takeIf { it.isNotBlank() }
        }
    }.joinToString("\n")

fun formatLatencySeconds(latencyMs: Long): String {
    val seconds = latencyMs / 1000.0
    return if (seconds >= 10) {
        String.format(Locale.US, "%.0fs", seconds)
    } else {
        String.format(Locale.US, "%.1fs", seconds)
    }
}

fun formatRoundedCost(cost: Double): String =
    String.format(Locale.US, "$%.2f", cost)

@Composable
private fun MarkdownTable(table: MessageMarkdownBlock.Table) {
    val columnCount = table.header.size.coerceAtLeast(1)
    val scrollState = rememberScrollState()
    val minCellWidth = 112.dp

    OperationalCard(
        tone = CardTone.Surface1,
        padding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TableRow(
                cells = table.header,
                columnCount = columnCount,
                cellMinWidth = minCellWidth,
                isHeader = true,
            )
            table.rows.forEach { row ->
                TableRow(
                    cells = row,
                    columnCount = columnCount,
                    cellMinWidth = minCellWidth,
                    isHeader = false,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    columnCount: Int,
    cellMinWidth: Dp,
    isHeader: Boolean,
) {
    val colors = MaterialTheme.appColors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(columnCount) { index ->
            val cell = cells.getOrNull(index).orEmpty()
            OperationalCard(
                modifier = Modifier.widthIn(min = cellMinWidth),
                tone = if (isHeader) CardTone.Surface2 else CardTone.Surface3,
                padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = cell,
                    style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                    color = if (isHeader) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

private fun parseMarkdownTable(lines: List<String>): MessageMarkdownBlock.Table? {
    if (lines.size < 2) return null
    val header = parseTableCells(lines.first())
    val separator = parseTableCells(lines[1])
    if (header.isEmpty() || separator.isEmpty()) return null
    if (!separator.all { it.matches(TABLE_SEPARATOR_REGEX) }) return null
    val rows = lines.drop(2).map { parseTableCells(it) }
    return MessageMarkdownBlock.Table(header = header, rows = rows)
}

private fun parseTableCells(line: String): List<String> =
    line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }

private fun String.isTableRow(): Boolean {
    val trimmed = trim()
    return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2
}

private fun String.isDivider(): Boolean =
    trim().matches(Regex("^(-{3,}|\\*{3,})$"))

private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.*)$")
private val TABLE_SEPARATOR_REGEX = Regex("^:?-{3,}:?$")
private val THINK_REGEX = Regex("<think>(.*?)</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

@Composable
private fun ReasoningBlock(
    text: String,
) {
    val colors = MaterialTheme.appColors
    var expanded by remember(text) { mutableStateOf(false) }
    val preview = remember(text) {
        text.replace("\n", " ").trim().let { value ->
            if (value.length > 120) "${value.take(120)}…" else value
        }
    }

    OperationalCard(
        modifier = Modifier.clickable { expanded = !expanded },
        tone = CardTone.Surface3,
        padding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataChip(text = "Model reasoning", tone = BadgeTone.Neutral)
                MetadataChip(
                    text = if (expanded) "Hide" else "Show",
                    tone = BadgeTone.Info,
                )
            }
            Text(
                text = if (expanded) text else preview.ifBlank { "Reasoning hidden." },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

private fun stripThinkTags(text: String): String =
    THINK_REGEX.replace(text) { "" }.trim()
