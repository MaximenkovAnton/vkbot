package com.simarel.vkbot.ai.adapter.output.ai.knowledge

import com.simarel.vkbot.ai.domain.exception.KnowledgeExtractionException
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

private const val JSON_FACTS =
    "{\"facts\":[{\"subjectId\":7600001,\"category\":\"INTEREST\"," +
        "\"key\":\"interest_football\",\"value\":\"футбол\",\"confidence\":0.9," +
        "\"sourceKind\":\"SELF_STATEMENT\",\"sourceConversationMessageId\":101}]}"
private const val JSON_NO_FACTS = """{"facts":[]}"""
private const val NOT_JSON = "не json"

class UserKnowledgeExtractionAiAdapterTest {

    private val objectMapper = ObjectMapperProvider.create()

    @Test
    fun `parsesJsonFactsIntoDrafts`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ JSON_FACTS }))
        val request = FakeKnowledgeProvider.createKnowledgeExtractionRequest()

        val response = KnowledgeExtractionAiAdapter(service, objectMapper).execute(request)

        assertEquals(listOf("[msg=101|from=7600001|Иван Петров]: люблю футбол"), service.calls.toList())
        val draft = response.facts.orEmpty().single()
        assertEquals(7600001, draft.subjectId)
        assertEquals("INTEREST", draft.category)
        assertEquals("interest_football", draft.key)
        assertEquals("футбол", draft.value)
        assertEquals(0.9, draft.confidence)
        assertEquals("SELF_STATEMENT", draft.sourceKind)
        assertEquals(101, draft.sourceConversationMessageId)
    }

    @Test
    fun `stripsOneJsonCodeFence`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ "```json\n$JSON_FACTS\n```" }))

        val response = KnowledgeExtractionAiAdapter(service, objectMapper).execute(
            FakeKnowledgeProvider.createKnowledgeExtractionRequest(),
        )

        assertEquals("футбол", response.facts.orEmpty().single().value)
        assertEquals(1, service.calls.size)
    }

    @Test
    fun `acceptsEmptyFactsArrayAsSuccess`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ JSON_NO_FACTS }))

        val response = KnowledgeExtractionAiAdapter(service, objectMapper).execute(
            FakeKnowledgeProvider.createKnowledgeExtractionRequest(),
        )

        assertTrue(response.facts.orEmpty().isEmpty())
        assertEquals(1, service.calls.size)
    }

    @Test
    fun `failsAfterThreeAttemptsOnInvalidJson`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ NOT_JSON }))

        val thrown = assertThrows<KnowledgeExtractionException> {
            KnowledgeExtractionAiAdapter(service, objectMapper).execute(
                FakeKnowledgeProvider.createKnowledgeExtractionRequest(),
            )
        }

        assertEquals(3, service.calls.size)
        assertEquals(ExceptionStatus.EXTERNAL_FAILURE, thrown.status())
    }

    @Test
    fun `failsAfterThreeAttemptsWhenFactsFieldIsMissing`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ "{}" }))

        assertThrows<KnowledgeExtractionException> {
            KnowledgeExtractionAiAdapter(service, objectMapper).execute(
                FakeKnowledgeProvider.createKnowledgeExtractionRequest(),
            )
        }

        assertEquals(3, service.calls.size)
    }

    @Test
    fun `logsOnlyAttemptAndErrorClass`() {
        val service = RecordingKnowledgeExtractionAiService(listOf({ NOT_JSON }))

        SystemLoggerCapture(KnowledgeExtractionAiAdapter::class.java.name).use { capture ->
            assertThrows<KnowledgeExtractionException> {
                KnowledgeExtractionAiAdapter(service, objectMapper).execute(
                    FakeKnowledgeProvider.createKnowledgeExtractionRequest(
                        messages = listOf("[msg=101|from=7600001|Иван Петров]: люблю футбол"),
                    ),
                )
            }

            val warnings = capture.messages(Level.WARNING)
            assertEquals(3, warnings.size)
            warnings.forEach { warning ->
                assertTrue(warning.contains("attempt"), warning)
                assertTrue(warning.contains("KnowledgeExtractionException"), warning)
                assertTrue(!warning.contains("люблю футбол"), warning)
                assertTrue(!warning.contains("футбол"), warning)
                assertTrue(!warning.contains("Иван Петров"), warning)
            }
        }
    }

    private class RecordingKnowledgeExtractionAiService(
        private val outcomes: List<() -> String>,
    ) : KnowledgeExtractionAiService {
        val calls = ConcurrentLinkedQueue<String>()

        override fun extract(messages: String): String {
            calls.add(messages)
            return outcomes[minOf(calls.size - 1, outcomes.lastIndex)].invoke()
        }
    }
}
