package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.ai.domain.knowledge.KnowledgeFactDraft
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.OffsetDateTime

private const val PEER_ID = 20L
private const val IVAN_ID = 7_600_001L
private const val PETR_ID = 7_600_002L
private val OBSERVED_AT: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

class UserKnowledgeValidatorTest {

    private val messages: List<StoredMessage> = listOf(
        FakeModelProvider.createStoredMessage(
            peerId = PEER_ID,
            conversationMessageId = 101,
            fromId = IVAN_ID,
            messageText = "люблю футбол",
            date = OBSERVED_AT,
        ),
        FakeModelProvider.createStoredMessage(
            peerId = PEER_ID,
            conversationMessageId = 102,
            fromId = PETR_ID,
            messageText = "играю в шахматы",
            date = OBSERVED_AT,
        ),
    )
    private val authorNames = mapOf(IVAN_ID to "Иван Петров", PETR_ID to "Пётр Иванов")

    @Test
    fun `acceptsSubjectByIdAndByNormalizedName`() {
        val candidates = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(subjectId = IVAN_ID),
            FakeKnowledgeProvider.createKnowledgeFactDraft(
                subjectId = null,
                subjectName = "  иван   петров ",
                key = "interest_reading",
                value = "чтение",
            ),
        )

        assertEquals(listOf(IVAN_ID, IVAN_ID), candidates.map { it.userId })
    }

    @Test
    fun `dropsUnknownAndAmbiguousSubjects`() {
        val ambiguousNames = mapOf(IVAN_ID to "Иван Петров", PETR_ID to "Иван Петров")

        val candidates = UserKnowledgeValidator.validate(
            drafts = listOf(
                FakeKnowledgeProvider.createKnowledgeFactDraft(subjectId = 999),
                FakeKnowledgeProvider.createKnowledgeFactDraft(subjectId = null, subjectName = "Никто"),
                FakeKnowledgeProvider.createKnowledgeFactDraft(subjectId = null, subjectName = null),
            ),
            messages = messages,
            authorNames = ambiguousNames,
            peerId = PEER_ID,
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `dropsInvalidCategoryKeyValueSourceAndConfidence`() {
        val tooLongValue = "a".repeat(UserFactCandidate.MAX_VALUE_LENGTH + 1)

        val candidates = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(category = "HOBBY"),
            FakeKnowledgeProvider.createKnowledgeFactDraft(key = "ключ"),
            FakeKnowledgeProvider.createKnowledgeFactDraft(key = "a".repeat(97)),
            FakeKnowledgeProvider.createKnowledgeFactDraft(value = "  "),
            FakeKnowledgeProvider.createKnowledgeFactDraft(value = tooLongValue),
            FakeKnowledgeProvider.createKnowledgeFactDraft(sourceConversationMessageId = 999),
            FakeKnowledgeProvider.createKnowledgeFactDraft(confidence = null),
            FakeKnowledgeProvider.createKnowledgeFactDraft(confidence = Double.NaN),
        )

        assertTrue(candidates.isEmpty())
    }

    @ParameterizedTest
    @CsvSource(
        "SELF_STATEMENT, 0.95, 0.95",
        "OTHER_STATEMENT, 0.95, 0.8",
        "CONGRATULATION, 1.0, 0.8",
        "INFERENCE, 0.9, 0.6",
        "SPECULATION, 0.9, 0.6",
        "SELF_STATEMENT, 1.5, 1.0",
    )
    fun `clampsConfidenceBySourceKind`(sourceKind: String, confidence: Double, expected: Double) {
        val candidate = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(sourceKind = sourceKind, confidence = confidence),
        ).single()

        assertEquals(expected, candidate.confidence)
    }

    @Test
    fun `downgradesSelfStatementWhenAuthorIsAnotherPerson`() {
        val candidate = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(
                subjectId = IVAN_ID,
                sourceKind = "SELF_STATEMENT",
                sourceConversationMessageId = 102,
            ),
        ).single()

        assertEquals(KnowledgeSourceKind.OTHER_STATEMENT, candidate.sourceKind)
        assertEquals(0.8, candidate.confidence)
    }

    @Test
    fun `deduplicatesCanonicalValues`() {
        val candidates = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(value = "  Футбол  "),
            FakeKnowledgeProvider.createKnowledgeFactDraft(value = "футбол"),
        )

        assertEquals(1, candidates.size)
        assertEquals("Футбол", candidates.single().value)
    }

    @Test
    fun `mapsSourceFieldsToCandidate`() {
        val candidate = validate(
            FakeKnowledgeProvider.createKnowledgeFactDraft(value = "футбол"),
        ).single()

        assertEquals(PEER_ID, candidate.peerId)
        assertEquals(UserFactCategory.INTEREST, candidate.category)
        assertEquals(101, candidate.conversationMessageId)
        assertEquals(IVAN_ID, candidate.saidById)
        assertEquals(OBSERVED_AT, candidate.observedAt)
        assertEquals(messages.first().id, candidate.messageId)
    }

    private fun validate(vararg drafts: KnowledgeFactDraft): List<UserFactCandidate> = UserKnowledgeValidator.validate(
        drafts = drafts.toList(),
        messages = messages,
        authorNames = authorNames,
        peerId = PEER_ID,
    )
}
