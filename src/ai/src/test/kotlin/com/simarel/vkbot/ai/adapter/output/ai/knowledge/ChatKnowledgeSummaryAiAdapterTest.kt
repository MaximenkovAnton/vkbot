package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.simarel.vkbot.ai.domain.exception.ChatKnowledgeSummaryException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

private const val SUMMARY_TEXT = "Темы: программирование, мемы и шутки."
private const val AUTHOR_LINE = "[Иван Петров]: люблю футбол"
private const val ATTEMPTS = 3

class ChatKnowledgeSummaryAiAdapterTest {

    @Test
    fun `returnsModelTextOnFirstSuccess`() {
        val service = RecordingChatKnowledgeSummarizer(listOf({ SUMMARY_TEXT }))

        val response = ChatKnowledgeSummaryAiAdapter(service).execute(
            FakeKnowledgeProvider.createChatKnowledgeSummaryRequest(messages = listOf(AUTHOR_LINE)),
        )

        assertEquals(SUMMARY_TEXT, response.text)
        assertEquals(1, service.calls.size)
        assertEquals(AUTHOR_LINE, service.calls.single())
    }

    @Test
    fun `retriesAndSucceedsOnSecondAttempt`() {
        val service = RecordingChatKnowledgeSummarizer(listOf({ "  " }, { SUMMARY_TEXT }))

        val response = ChatKnowledgeSummaryAiAdapter(service).execute(
            FakeKnowledgeProvider.createChatKnowledgeSummaryRequest(),
        )

        assertEquals(SUMMARY_TEXT, response.text)
        assertEquals(2, service.calls.size)
    }

    @Test
    fun `failsAfterThreeAttemptsOnBlankResponse`() {
        val service = RecordingChatKnowledgeSummarizer(listOf({ " " }))

        val thrown = assertThrows<ChatKnowledgeSummaryException> {
            ChatKnowledgeSummaryAiAdapter(service).execute(
                FakeKnowledgeProvider.createChatKnowledgeSummaryRequest(),
            )
        }

        assertEquals(ATTEMPTS, service.calls.size)
        assertTrue(thrown.message.orEmpty().contains("after $ATTEMPTS attempts"), thrown.message)
    }

    @Test
    fun `logsOnlyAttemptAndErrorClass`() {
        val service = RecordingChatKnowledgeSummarizer(listOf({ " " }))

        SystemLoggerCapture(ChatKnowledgeSummaryAiAdapter::class.java.name).use { capture ->
            assertThrows<ChatKnowledgeSummaryException> {
                ChatKnowledgeSummaryAiAdapter(service).execute(
                    FakeKnowledgeProvider.createChatKnowledgeSummaryRequest(messages = listOf(AUTHOR_LINE)),
                )
            }

            val warnings = capture.messages(Level.WARNING)
            assertEquals(ATTEMPTS, warnings.size)
            assertEquals(
                "Chat knowledge summary attempt 1 failed: ChatKnowledgeSummaryException",
                warnings.first(),
            )
            warnings.forEach { warning ->
                assertTrue(!warning.contains("люблю футбол"), warning)
                assertTrue(!warning.contains("Иван Петров"), warning)
            }
        }
    }

    private class RecordingChatKnowledgeSummarizer(
        private val outcomes: List<() -> String>,
    ) : ChatKnowledgeSummarizer {
        val calls = ConcurrentLinkedQueue<String>()

        override fun summarize(messages: String): String {
            calls.add(messages)
            return outcomes[minOf(calls.size - 1, outcomes.lastIndex)].invoke()
        }
    }
}
