package com.simarel.vkbot.ai.adapter.output.ai.summarization

import com.simarel.vkbot.ai.domain.exception.SummaryGenerationException
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val MESSAGES = "Ivan Petrov: hello"

class SummarizationAiAdapterTest {

    @Test
    fun `execute returns the summary on the first attempt`() {
        val summarizer = RecordingConversationSummarizer(
            listOf<() -> SummaryResponse>({ FakeSummaryProvider.createSummarizationServiceResponse("short", "full") }),
        )

        val response = adapter(summarizer).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))

        assertEquals("short", response.shortSummary)
        assertEquals("full", response.fullSummary)
        assertEquals(listOf(MESSAGES), summarizer.calls.toList())
    }

    @Test
    fun `execute retries once after a failure and returns the second response`() {
        val summarizer = RecordingConversationSummarizer(
            listOf<() -> SummaryResponse>(
                { throw IllegalStateException("first attempt failed") },
                { FakeSummaryProvider.createSummarizationServiceResponse("second short", "second full") },
            ),
        )

        val response = adapter(summarizer).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))

        assertEquals("second short", response.shortSummary)
        assertEquals("second full", response.fullSummary)
        assertEquals(2, summarizer.calls.size)
    }

    @Test
    fun `execute retries three times and rethrows the last failure`() {
        val failures = listOf(
            IllegalStateException("attempt 1"),
            IllegalStateException("attempt 2"),
            IllegalStateException("attempt 3"),
        )
        val summarizer = RecordingConversationSummarizer(failures.map { failure -> { throw failure } })

        val thrown = assertThrows(SummaryGenerationException::class.java) {
            adapter(summarizer).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))
        }

        assertSame(failures.last(), thrown.cause)
        assertEquals(3, summarizer.calls.size)
    }

    private fun adapter(summarizer: ConversationSummarizer) = SummarizationAiAdapter(summarizer)

    private class RecordingConversationSummarizer(
        private val outcomes: List<() -> SummaryResponse>,
    ) : ConversationSummarizer {
        val calls = ConcurrentLinkedQueue<String>()

        override fun generateSummary(messages: String): SummaryResponse {
            calls.add(messages)
            return outcomes[calls.size - 1]()
        }
    }
}
