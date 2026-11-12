package com.simarel.vkbot.persistence.domain.knowledge

import com.simarel.vkbot.persistence.testfixtures.knowledge.UserKnowledgeReadProvider
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.OffsetDateTime
import java.util.stream.Stream

private const val USER_ID = 10L
private const val OTHER_USER_ID = 20L
private const val SUMMARY_TEXT = "сводка"
private val SUMMARY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")
private val NEXT_DAY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-02T00:00:00Z")

class UserKnowledgeReadModelTest {

    @Test
    fun `returnsEntryForEveryRequestedUser`() {
        val fact = FakeModelProvider.createUserFact(userId = USER_ID, lastConfirmedAt = SUMMARY_AT)
        val summary = UserKnowledgeReadProvider.createStoredUserKnowledgeSummary(
            userId = USER_ID,
            summaryText = SUMMARY_TEXT,
            lastFactAt = SUMMARY_AT,
            summarizedAt = SUMMARY_AT,
        )

        val knowledge = UserKnowledgeReadModel.assemble(
            listOf(USER_ID, OTHER_USER_ID),
            listOf(fact),
            listOf(summary),
        )

        assertEquals(setOf(USER_ID, OTHER_USER_ID), knowledge.keys)
        assertEquals(
            FakeModelProvider.createUserKnowledge(facts = listOf(fact), summary = SUMMARY_TEXT, summaryFresh = true),
            knowledge[USER_ID],
        )
        assertEquals(FakeModelProvider.createUserKnowledge(), knowledge[OTHER_USER_ID])
    }

    @Test
    fun `returnsSingleEntryForRepeatedUserId`() {
        val knowledge = UserKnowledgeReadModel.assemble(
            listOf(USER_ID, OTHER_USER_ID, USER_ID),
            emptyList(),
            emptyList(),
        )

        assertEquals(setOf(USER_ID, OTHER_USER_ID), knowledge.keys)
    }

    @Test
    fun `returnsEmptyMapForEmptyRequestedUsers`() {
        assertEquals(
            emptyMap<Long, UserKnowledge>(),
            UserKnowledgeReadModel.assemble(emptyList(), emptyList(), emptyList()),
        )
    }

    @Test
    fun `keepsFactOrderOfTheGivenList`() {
        val facts = listOf(
            FakeModelProvider.createUserFact(userId = USER_ID, value = "теннис"),
            FakeModelProvider.createUserFact(userId = USER_ID, value = "футбол"),
        )

        val knowledge = UserKnowledgeReadModel.assemble(listOf(USER_ID), facts, emptyList())

        assertEquals(listOf("теннис", "футбол"), knowledge.getValue(USER_ID).facts.map(UserFact::value))
    }

    @ParameterizedTest
    @MethodSource("summaryFreshnessCases")
    fun `computesSummaryFreshnessFromStoredAndFactTimestamps`(
        summaryLastFactAt: OffsetDateTime,
        factLastConfirmedAt: OffsetDateTime?,
        expectedFresh: Boolean,
    ) {
        val summary = UserKnowledgeReadProvider.createStoredUserKnowledgeSummary(
            userId = USER_ID,
            summaryText = SUMMARY_TEXT,
            lastFactAt = summaryLastFactAt,
            summarizedAt = SUMMARY_AT,
        )
        val facts = factLastConfirmedAt
            ?.let { listOf(FakeModelProvider.createUserFact(userId = USER_ID, lastConfirmedAt = it)) }
            .orEmpty()

        val knowledge = UserKnowledgeReadModel.assemble(listOf(USER_ID), facts, listOf(summary)).getValue(USER_ID)

        assertEquals(SUMMARY_TEXT, knowledge.summary)
        assertEquals(expectedFresh, knowledge.summaryFresh)
    }

    @Test
    fun `returnsNoSummaryWhenUserHasNoSummaryRow`() {
        val knowledge = UserKnowledgeReadModel.assemble(listOf(USER_ID), emptyList(), emptyList()).getValue(USER_ID)

        assertNull(knowledge.summary)
        assertFalse(knowledge.summaryFresh)
    }

    private companion object {
        @JvmStatic
        fun summaryFreshnessCases(): Stream<Arguments> = Stream.of(
            Arguments.of(SUMMARY_AT, SUMMARY_AT, true),
            Arguments.of(NEXT_DAY_AT, null, false),
            Arguments.of(SUMMARY_AT, NEXT_DAY_AT, false),
        )
    }
}
