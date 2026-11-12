package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val AUTHOR_ID = 7_600_001L

class UserKnowledgeBatchFormatterTest {

    @Test
    fun `rendersAuthorIdNameAndText`() {
        val messages = listOf(
            FakeModelProvider.createStoredMessage(fromId = AUTHOR_ID, messageText = "люблю футбол"),
            FakeModelProvider.createStoredMessage(fromId = 7_600_002, messageText = "играю в шахматы"),
        )

        val lines = UserKnowledgeBatchFormatter.format(
            messages = messages,
            authorNames = mapOf(AUTHOR_ID to "Иван Петров"),
        )

        assertEquals(
            listOf(
                "[7600001|Иван Петров]: люблю футбол",
                "[7600002|Пользователь 7600002]: играю в шахматы",
            ),
            lines,
        )
    }

    @Test
    fun `fallsBackToPlaceholderWhenNameIsMissing`() {
        val lines = UserKnowledgeBatchFormatter.format(
            messages = listOf(FakeModelProvider.createStoredMessage(fromId = AUTHOR_ID, messageText = "привет")),
            authorNames = emptyMap(),
        )

        assertEquals(listOf("[7600001|Пользователь 7600001]: привет"), lines)
    }
}
