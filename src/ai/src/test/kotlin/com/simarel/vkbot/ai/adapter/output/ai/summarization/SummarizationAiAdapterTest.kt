package com.simarel.vkbot.ai.adapter.output.ai.summarization

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
        val service = RecordingSummarizationAiService(
            listOf<() -> SummaryResponse>({ FakeSummaryProvider.createSummarizationServiceResponse("short", "full") }),
        )

        val response = adapter(service).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))

        assertEquals("short", response.shortSummary)
        assertEquals("full", response.fullSummary)
        assertEquals(listOf(MESSAGES), service.calls.toList())
    }

    @Test
    fun `execute retries once after a failure and returns the second response`() {
        val service = RecordingSummarizationAiService(
            listOf<() -> SummaryResponse>(
                { throw IllegalStateException("first attempt failed") },
                { FakeSummaryProvider.createSummarizationServiceResponse("second short", "second full") },
            ),
        )

        val response = adapter(service).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))

        assertEquals("second short", response.shortSummary)
        assertEquals("second full", response.fullSummary)
        assertEquals(2, service.calls.size)
    }

    @Test
    fun `execute retries three times and rethrows the last failure`() {
        val failures = listOf(
            IllegalStateException("attempt 1"),
            IllegalStateException("attempt 2"),
            IllegalStateException("attempt 3"),
        )
        val service = RecordingSummarizationAiService(failures.map { failure -> { throw failure } })

        val thrown = assertThrows(IllegalStateException::class.java) {
            adapter(service).execute(FakeSummaryProvider.createSummarizationRequest(MESSAGES))
        }

        assertSame(failures.last(), thrown)
        assertEquals(3, service.calls.size)
    }

    private fun adapter(service: SummarizationAiService) = SummarizationAiAdapter(service)

    private class RecordingSummarizationAiService(
        private val outcomes: List<() -> SummaryResponse>,
    ) : SummarizationAiService {
        val calls = ConcurrentLinkedQueue<String>()

        override fun generateSummary(messages: String): SummaryResponse {
            calls.add(messages)
            return outcomes[calls.size - 1]()
        }
    }
}
