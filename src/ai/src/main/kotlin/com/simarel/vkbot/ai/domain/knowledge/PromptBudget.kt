package com.simarel.vkbot.ai.domain.knowledge

private const val ELLIPSIS = "…"
private const val SINGLE_CHAR_LINE = 1
private const val SEPARATOR = 1

object PromptBudget {

    fun fit(lines: List<String>, maxChars: Int): List<String> {
        if (lines.isEmpty() || maxChars <= 0) {
            return lines
        }
        val separators = (lines.size - SEPARATOR).coerceAtLeast(0)
        val perLine = ((maxChars - separators) / lines.size).coerceAtLeast(SINGLE_CHAR_LINE)
        return lines.map { line -> truncate(line, perLine) }
    }

    private fun truncate(line: String, limit: Int): String = when {
        line.length <= limit -> line
        limit <= SINGLE_CHAR_LINE -> ELLIPSIS
        else -> line.take(limit - SEPARATOR) + ELLIPSIS
    }
}
