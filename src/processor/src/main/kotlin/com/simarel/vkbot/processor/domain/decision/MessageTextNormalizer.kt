package com.simarel.vkbot.processor.domain.decision

import java.text.Normalizer

private const val DENSE_PARTS_MIN = 8
private const val DENSE_PART_AVERAGE = 2.0

private val INVISIBLE_CHARS = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD')
private val LOWERCASE_CYRILLIC = Regex("[а-яё]")
private val SEPARATOR = Regex("[ .\\-*_]")
private val SEPARATOR_RUN = Regex("[ .\\-*_]{2,}")
private val PUNCTUATION = Regex("[,;:!?\"«»]")
private val UNICODE_WHITESPACE = Regex("(?U)\\s+")

private val HOMOGLYPHS = mapOf(
    'a' to 'а', 'e' to 'е', 'o' to 'о', 'p' to 'р', 'c' to 'с', 'x' to 'х',
    'y' to 'у', 'k' to 'к', 'm' to 'м', 't' to 'т', 'h' to 'н', 'b' to 'ь',
    'A' to 'А', 'E' to 'Е', 'O' to 'О', 'P' to 'Р', 'C' to 'С', 'X' to 'Х',
    'Y' to 'У', 'K' to 'К', 'M' to 'М', 'T' to 'Т', 'H' to 'Н', 'B' to 'В',
)

private val LEET = mapOf('0' to 'о', '3' to 'з', '4' to 'ч', '6' to 'б')

object MessageTextNormalizer {
    fun normalize(text: String): String {
        val withoutInvisibles = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .filter { it !in INVISIBLE_CHARS }

        val separated = collapseDenseSeparators(withoutInvisibles)

        val homoglyphReplaced = separated.split(" ")
            .joinToString(" ") { word ->
                if (LOWERCASE_CYRILLIC.containsMatchIn(word)) {
                    word.map { HOMOGLYPHS[it] ?: it }.joinToString("")
                } else {
                    word
                }
            }

        val leetReplaced = replaceLeet(homoglyphReplaced)
            .replace('ё', 'е')
            .replace('Ё', 'Е')

        return UNICODE_WHITESPACE.replace(leetReplaced, " ").trim()
    }

    private fun collapseDenseSeparators(text: String): String {
        val parts = text.split(SEPARATOR).filter { it.isNotEmpty() }
        val dense = parts.size >= DENSE_PARTS_MIN &&
            parts.sumOf { it.length }.toDouble() / parts.size < DENSE_PART_AVERAGE
        if (!dense) return text

        return text.replace(SEPARATOR_RUN, "\u0000")
            .replace(SEPARATOR, "")
            .replace("\u0000", " ")
            .replace(PUNCTUATION, " ")
    }

    private fun replaceLeet(text: String): String = buildString {
        text.forEachIndexed { index, char ->
            val replacement = LEET[char]
            if (replacement != null && isLeetPosition(text, index)) {
                append(replacement)
            } else {
                append(char)
            }
        }
    }

    private fun isLeetPosition(text: String, index: Int): Boolean {
        if (index == 0 || index == text.lastIndex) return false
        return text.getOrNull(index - 1).isLeetNeighbour() ||
            text.getOrNull(index + 1).isLeetNeighbour()
    }

    private fun Char?.isLeetNeighbour(): Boolean = this != null && lowercaseChar() in 'а'..'я'
}
