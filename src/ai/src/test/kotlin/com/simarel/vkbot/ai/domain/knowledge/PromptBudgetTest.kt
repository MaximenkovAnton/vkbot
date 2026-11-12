package com.simarel.vkbot.ai.domain.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val BUDGET = 100

class PromptBudgetTest {

    @Test
    fun `keepsShortLinesUntouched`() {
        val lines = listOf("[a]: привет", "[b]: как дела")

        assertEquals(lines, PromptBudget.fit(lines, BUDGET))
    }

    @Test
    fun `truncatesLongLinesEvenlyToStayInsideBudget`() {
        val lines = listOf("a".repeat(500), "b".repeat(500), "c".repeat(500))

        val fitted = PromptBudget.fit(lines, BUDGET)

        assertEquals(3, fitted.size)
        val renderedLength = fitted.sumOf { it.length + 1 }
        assertTrue(renderedLength <= BUDGET, "prompt must fit the budget: $renderedLength")
        fitted.forEach { line ->
            assertTrue(line.endsWith("…"), line)
        }
    }

    @Test
    fun `fitsSingleLongLineIntoBudget`() {
        val fitted = PromptBudget.fit(listOf("x".repeat(1000)), BUDGET)

        assertEquals(1, fitted.size)
        assertTrue(fitted.single().length <= BUDGET, "line length ${fitted.single().length}")
    }

    @Test
    fun `returnsLinesUnchangedWhenBudgetIsNotSet`() {
        val lines = listOf("x".repeat(1000))

        assertEquals(lines, PromptBudget.fit(lines, 0))
    }

    @Test
    fun `returnsEmptyListForEmptyInput`() {
        assertEquals(emptyList<String>(), PromptBudget.fit(emptyList(), BUDGET))
    }
}
