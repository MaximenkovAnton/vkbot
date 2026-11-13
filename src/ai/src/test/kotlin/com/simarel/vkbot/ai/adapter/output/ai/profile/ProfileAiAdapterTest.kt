package com.simarel.vkbot.ai.adapter.output.ai.profile

import com.simarel.vkbot.ai.domain.exception.ProfileGenerationException
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.ConcurrentLinkedQueue

private const val QUESTION = "что ты знаешь о Паше Селезневе"
private const val DATA = "Профиль:\nИмя: Паша Селезнев"

class ProfileAiAdapterTest {

    @Test
    fun `execute retries three times and rethrows`() {
        val failure = RuntimeException("boom")
        val service = RecordingProfileAnswerGenerator(failure = failure)

        val thrown = assertThrows<ProfileGenerationException> {
            ProfileAiAdapter(service).execute(request())
        }

        assertEquals(3, service.calls.size)
        assertSame(failure, thrown.cause)
        assertEquals(ExceptionStatus.EXTERNAL_FAILURE, thrown.status())
    }

    @ParameterizedTest
    @ValueSource(strings = ["   ", "", "\t\n"])
    fun `execute treats a blank answer as a failed attempt`(blankAnswer: String) {
        val service = RecordingProfileAnswerGenerator(
            listOf(
                { blankAnswer },
                { blankAnswer },
                { blankAnswer },
            ),
        )

        val thrown = assertThrows<ProfileGenerationException> {
            ProfileAiAdapter(service).execute(request())
        }

        assertEquals(3, service.calls.size)
        assertEquals(ExceptionStatus.EXTERNAL_FAILURE, thrown.status())
    }

    @Test
    fun `execute returns the first non blank answer without retrying`() {
        val service = RecordingProfileAnswerGenerator(listOf({ "досье" }))
        val request = request()

        val response = ProfileAiAdapter(service).execute(request)

        assertEquals("досье", response.text)
        assertEquals(1, service.calls.size)
        assertEquals(listOf(request.question to request.data), service.calls.toList())
    }

    private fun request() = FakeProfileProvider.createProfileAnswerGenerationRequest(
        question = QUESTION,
        data = DATA,
    )

    private class RecordingProfileAnswerGenerator(
        private val outcomes: List<() -> String> = emptyList(),
        private val failure: Exception? = null,
    ) : ProfileAnswerGenerator {
        val calls = ConcurrentLinkedQueue<Pair<String, String>>()

        override fun answer(question: String, data: String): String {
            calls.add(question to data)
            failure?.let { throw it }
            return outcomes[calls.size - 1]()
        }
    }
}
