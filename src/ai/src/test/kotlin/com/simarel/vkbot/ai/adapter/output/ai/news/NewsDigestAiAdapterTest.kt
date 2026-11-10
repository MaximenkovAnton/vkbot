package com.simarel.vkbot.ai.adapter.output.ai.news

import com.simarel.vkbot.ai.domain.exception.NewsDigestGenerationException
import com.simarel.vkbot.ai.testfixtures.FakeNewsDigestProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val DATA = "digest data"

class NewsDigestAiAdapterTest {

    @Test
    fun `execute returns the digest on the first attempt`() {
        val service = RecordingNewsDigestAiService(listOf({ "digest from model" }))

        val response = NewsDigestAiAdapter(service).execute(request())

        assertEquals("digest from model", response.text)
        assertEquals(listOf(DATA), service.calls.toList())
    }

    @Test
    fun `execute retries after a failure and returns the second response`() {
        val service = RecordingNewsDigestAiService(
            listOf(
                { throw IllegalStateException("first attempt failed") },
                { "second attempt digest" },
            ),
        )

        val response = NewsDigestAiAdapter(service).execute(request())

        assertEquals("second attempt digest", response.text)
        assertEquals(2, service.calls.size)
    }

    @Test
    fun `execute retries three times and rethrows the last failure`() {
        val failures = listOf(
            IllegalStateException("attempt 1"),
            IllegalStateException("attempt 2"),
            IllegalStateException("attempt 3"),
        )
        val service = RecordingNewsDigestAiService(failures.map { failure -> { throw failure } })

        val thrown = assertThrows(NewsDigestGenerationException::class.java) {
            NewsDigestAiAdapter(service).execute(request())
        }

        assertSame(failures.last(), thrown.cause)
        assertEquals(3, service.calls.size)
    }

    @Test
    fun `execute treats a blank digest as a failed attempt`() {
        val service = RecordingNewsDigestAiService(listOf({ "   " }, { "second attempt digest" }))

        val response = NewsDigestAiAdapter(service).execute(request())

        assertEquals("second attempt digest", response.text)
        assertEquals(2, service.calls.size)
    }

    private fun request() = FakeNewsDigestProvider.createNewsDigestGenerationRequest(DATA)

    private class RecordingNewsDigestAiService(
        private val outcomes: List<() -> String>,
    ) : NewsDigestAiService {
        val calls = ConcurrentLinkedQueue<String>()

        override fun generateDigest(data: String): String {
            calls.add(data)
            return outcomes[calls.size - 1]()
        }
    }
}
