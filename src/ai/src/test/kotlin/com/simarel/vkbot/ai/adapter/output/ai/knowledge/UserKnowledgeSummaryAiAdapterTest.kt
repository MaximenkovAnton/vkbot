package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.simarel.vkbot.ai.domain.exception.UserKnowledgeSummaryException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

private const val ATTEMPTS = 3
private const val FACT_LINE = "Интересы и увлечения:\n- футбол"
private const val SUMMARY_TEXT = "Иван увлекается футболом и историей."

class UserKnowledgeSummaryAiAdapterTest {

    @Test
    fun `returnsModelTextOnFirstSuccess`() {
        val service = RecordingUserKnowledgeSummaryAiService(listOf({ SUMMARY_TEXT }))

        val response = UserKnowledgeSummaryAiAdapter(service).execute(
            FakeKnowledgeProvider.createUserKnowledgeSummaryRequest(facts = FACT_LINE),
        )

        assertEquals(SUMMARY_TEXT, response.text)
        assertEquals(1, service.calls.size)
        assertEquals(FACT_LINE, service.calls.single())
    }

    @Test
    fun `retriesAndSucceedsOnSecondAttempt`() {
        val service = RecordingUserKnowledgeSummaryAiService(listOf({ "  " }, { SUMMARY_TEXT }))

        val response = UserKnowledgeSummaryAiAdapter(service).execute(
            FakeKnowledgeProvider.createUserKnowledgeSummaryRequest(facts = FACT_LINE),
        )

        assertEquals(SUMMARY_TEXT, response.text)
        assertEquals(2, service.calls.size)
    }

    @Test
    fun `failsAfterThreeAttemptsOnThrownException`() {
        val service = RecordingUserKnowledgeSummaryAiService(
            listOf({ throw IllegalStateException("model unavailable") }),
        )

        val thrown = assertThrows<UserKnowledgeSummaryException> {
            UserKnowledgeSummaryAiAdapter(service).execute(
                FakeKnowledgeProvider.createUserKnowledgeSummaryRequest(facts = FACT_LINE),
            )
        }

        assertEquals(ATTEMPTS, service.calls.size)
        assertTrue(thrown.message.orEmpty().contains("after $ATTEMPTS attempts"), thrown.message)
        val wrapped = thrown.cause as UserKnowledgeSummaryException
        assertEquals("User knowledge summary generation failed", wrapped.message)
        assertTrue(wrapped.cause is IllegalStateException, wrapped.cause?.javaClass?.name)
    }

    @Test
    fun `failsAfterThreeAttemptsOnBlankResponse`() {
        val service = RecordingUserKnowledgeSummaryAiService(listOf({ " " }))

        val thrown = assertThrows<UserKnowledgeSummaryException> {
            UserKnowledgeSummaryAiAdapter(service).execute(
                FakeKnowledgeProvider.createUserKnowledgeSummaryRequest(facts = FACT_LINE),
            )
        }

        assertEquals(ATTEMPTS, service.calls.size)
        assertTrue(thrown.message.orEmpty().contains("after $ATTEMPTS attempts"), thrown.message)
    }

    @Test
    fun `logsOnlyAttemptAndErrorClass`() {
        val service = RecordingUserKnowledgeSummaryAiService(listOf({ " " }))

        SystemLoggerCapture(UserKnowledgeSummaryAiAdapter::class.java.name).use { capture ->
            assertThrows<UserKnowledgeSummaryException> {
                UserKnowledgeSummaryAiAdapter(service).execute(
                    FakeKnowledgeProvider.createUserKnowledgeSummaryRequest(facts = FACT_LINE),
                )
            }

            val warnings = capture.messages(Level.WARNING)
            assertEquals(ATTEMPTS, warnings.size)
            assertEquals(
                "User knowledge summary attempt 1 failed: UserKnowledgeSummaryException",
                warnings.first(),
            )
            warnings.forEach { warning ->
                assertTrue(!warning.contains("футбол"), warning)
                assertTrue(!warning.contains("Иван"), warning)
            }
        }
    }

    private class RecordingUserKnowledgeSummaryAiService(
        private val outcomes: List<() -> String>,
    ) : UserKnowledgeSummaryAiService {
        val calls = ConcurrentLinkedQueue<String>()

        override fun summarize(facts: String): String {
            calls.add(facts)
            return outcomes[minOf(calls.size - 1, outcomes.lastIndex)].invoke()
        }
    }
}
