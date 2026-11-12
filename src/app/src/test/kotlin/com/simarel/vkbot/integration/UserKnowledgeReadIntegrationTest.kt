package com.simarel.vkbot.integration

import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

private const val USER_WITH_DATA = 7_300_001L
private const val USER_WITHOUT_DATA = 7_300_002L
private const val USER_CARDS = 7_300_010L
private const val USER_STALE = 7_300_020L
private const val USER_FRESH_SUMMARY = 7_300_031L
private const val USER_STALE_SUMMARY = 7_300_032L
private const val USER_NEW_FACT_SUMMARY = 7_300_033L
private const val USER_NO_SUMMARY = 7_300_034L
private const val USER_BIRTH_YEAR = 7_300_040L
private const val USER_READ_ONLY = 7_300_050L
private const val READ_ONLY_PEER = 7_300_051L
private const val SPORT_FACT_KEY = "interest_sport"
private const val CONFIDENCE_DELTA = 0.0000001
private const val LONG_AGO_YEARS = 10L
private const val STALE_EVENT_DAYS = 31L
private const val FRESH_EVENT_DAYS = 29L
private const val STALE_HABIT_DAYS = 181L
private const val STALE_HISTORICAL_DAYS = 40L
private val SUMMARY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")
private val NEXT_DAY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-02T00:00:00Z")
private val CARD_WITH_CONFLICT_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")
private val CARD_CONFIRMED_AT: OffsetDateTime = OffsetDateTime.parse("2026-09-01T00:00:00Z")
private val CARD_HISTORICAL_AT: OffsetDateTime = OffsetDateTime.parse("2026-08-15T00:00:00Z")
private val BIRTH_AGE_OBSERVED_AT: OffsetDateTime = OffsetDateTime.parse("2010-06-01T00:00:00Z")

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class UserKnowledgeReadIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findUserKnowledgePort: FindUserKnowledgePort

    @Inject
    lateinit var saveUserFactsPort: SaveUserFactsPort

    private lateinit var fixtures: UserKnowledgeReadFixtures

    @BeforeEach
    fun setUp() {
        fixtures = UserKnowledgeReadFixtures(dataSource)
        clearKnowledge()
    }

    @AfterEach
    fun tearDown() {
        clearKnowledge()
    }

    @Test
    fun `returnsEntryPerRequestedUser`() {
        fixtures.insertFact(userId = USER_WITH_DATA, value = "футбол", lastConfirmedAt = SUMMARY_AT)
        fixtures.insertSummary(USER_WITH_DATA, "сводка", SUMMARY_AT, SUMMARY_AT)

        val response = findUserKnowledgePort.execute(
            FakePersistencePortProvider.createFindUserKnowledgeRequest(
                userIds = listOf(
                    FakeVoProvider.createFromId(USER_WITH_DATA),
                    FakeVoProvider.createFromId(USER_WITHOUT_DATA),
                    FakeVoProvider.createFromId(USER_WITH_DATA),
                ),
            ),
        )

        assertEquals(
            setOf(FakeVoProvider.createFromId(USER_WITH_DATA), FakeVoProvider.createFromId(USER_WITHOUT_DATA)),
            response.knowledge.keys,
        )
        assertEquals(REQUESTED_USER_COUNT, response.knowledge.size)
        val withData = response.knowledge.getValue(FakeVoProvider.createFromId(USER_WITH_DATA))
        assertEquals(1, withData.facts.size)
        assertEquals("футбол", withData.facts.single().value)
        assertEquals("сводка", withData.summary)
        assertTrue(withData.summaryFresh)
        assertEquals(
            FakeModelProvider.createUserKnowledge(),
            response.knowledge.getValue(FakeVoProvider.createFromId(USER_WITHOUT_DATA)),
        )
    }

    @Test
    fun `returnsCurrentAndHistoricalCardsInOrder`() {
        fixtures.insertFact(
            userId = USER_CARDS,
            value = "теннис",
            lastConfirmedAt = CARD_HISTORICAL_AT,
            status = UserFactStatus.HISTORICAL,
            supersededAt = CARD_HISTORICAL_AT,
        )
        fixtures.insertFact(
            userId = USER_CARDS,
            value = "футбол",
            lastConfirmedAt = CARD_CONFIRMED_AT,
        )
        fixtures.insertFact(
            userId = USER_CARDS,
            value = "хоккей",
            lastConfirmedAt = CARD_WITH_CONFLICT_AT,
            status = UserFactStatus.CANDIDATE,
            confidence = 0.75,
            conflict = true,
        )

        val knowledge = knowledgeOf(USER_CARDS).getValue(USER_CARDS)
        val facts = knowledge.facts

        assertEquals(listOf("хоккей", "футбол", "теннис"), facts.map(UserFact::value))
        assertEquals(
            listOf(UserFactStatus.CANDIDATE, UserFactStatus.CONFIRMED, UserFactStatus.HISTORICAL),
            facts.map(UserFact::status),
        )
        assertEquals(listOf(0.750, 0.900, 0.900), facts.map(UserFact::confidence))
        assertEquals(listOf(true, false, false), facts.map(UserFact::conflict))
        assertEquals(listOf(null, null, CARD_HISTORICAL_AT), facts.map(UserFact::supersededAt))
        assertTrue(facts.all { it.category == UserFactCategory.INTEREST })
        assertTrue(facts.all { it.factKey == SPORT_FACT_KEY })
        assertNull(knowledge.summary)
        assertFalse(knowledge.summaryFresh)
    }

    @Test
    fun `computesStaleByFreshnessWindow`() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        fixtures.insertFact(
            userId = USER_STALE,
            value = "концерт",
            lastConfirmedAt = now.minusDays(STALE_EVENT_DAYS),
            factKey = "event_concert",
            category = UserFactCategory.EVENT,
        )
        fixtures.insertFact(
            userId = USER_STALE,
            value = "кино",
            lastConfirmedAt = now.minusDays(FRESH_EVENT_DAYS),
            factKey = "event_cinema",
            category = UserFactCategory.EVENT,
        )
        fixtures.insertFact(
            userId = USER_STALE,
            value = "бег",
            lastConfirmedAt = now.minusDays(STALE_HABIT_DAYS),
            factKey = "habit_running",
            category = UserFactCategory.HABIT,
        )
        fixtures.insertFact(
            userId = USER_STALE,
            value = "футбол",
            lastConfirmedAt = now.minusYears(LONG_AGO_YEARS),
            factKey = "interest_football",
        )
        fixtures.insertFact(
            userId = USER_STALE,
            value = "поездка",
            lastConfirmedAt = now.minusDays(STALE_HISTORICAL_DAYS),
            factKey = "event_trip",
            category = UserFactCategory.EVENT,
            status = UserFactStatus.HISTORICAL,
            supersededAt = now.minusDays(STALE_HISTORICAL_DAYS),
        )

        val facts = knowledgeOf(USER_STALE).getValue(USER_STALE).facts

        assertEquals(
            mapOf(
                "event_concert" to true,
                "event_cinema" to false,
                "habit_running" to true,
                "interest_football" to false,
                "event_trip" to true,
            ),
            facts.associate { it.factKey to it.stale },
        )
    }

    @Test
    fun `returnsSummaryWithFreshness`() {
        fixtures.insertSummary(USER_FRESH_SUMMARY, "сводка", SUMMARY_AT, SUMMARY_AT)
        fixtures.insertFact(userId = USER_FRESH_SUMMARY, value = "футбол", lastConfirmedAt = SUMMARY_AT)
        fixtures.insertSummary(USER_STALE_SUMMARY, "старая сводка", NEXT_DAY_AT, SUMMARY_AT)
        fixtures.insertSummary(USER_NEW_FACT_SUMMARY, "сводка", SUMMARY_AT, SUMMARY_AT)
        fixtures.insertFact(userId = USER_NEW_FACT_SUMMARY, value = "футбол", lastConfirmedAt = NEXT_DAY_AT)

        val knowledge = knowledgeOf(USER_FRESH_SUMMARY, USER_STALE_SUMMARY, USER_NEW_FACT_SUMMARY, USER_NO_SUMMARY)

        assertEquals("сводка", knowledge.getValue(USER_FRESH_SUMMARY).summary)
        assertTrue(knowledge.getValue(USER_FRESH_SUMMARY).summaryFresh)
        assertEquals("старая сводка", knowledge.getValue(USER_STALE_SUMMARY).summary)
        assertFalse(knowledge.getValue(USER_STALE_SUMMARY).summaryFresh)
        assertEquals("сводка", knowledge.getValue(USER_NEW_FACT_SUMMARY).summary)
        assertFalse(knowledge.getValue(USER_NEW_FACT_SUMMARY).summaryFresh)
        assertNull(knowledge.getValue(USER_NO_SUMMARY).summary)
        assertFalse(knowledge.getValue(USER_NO_SUMMARY).summaryFresh)
    }

    @Test
    fun `readsNormalizedBirthYearAndPayload`() {
        saveUserFactsPort.execute(
            FakePersistencePortProvider.createSaveUserFactsRequest(
                candidates = listOf(
                    FakeModelProvider.createUserFactCandidate(
                        userId = USER_BIRTH_YEAR,
                        peerId = USER_BIRTH_YEAR,
                        category = UserFactCategory.PROFILE_ATTRIBUTE,
                        factKey = "age",
                        value = "20",
                        sourceKind = KnowledgeSourceKind.SELF_STATEMENT,
                        confidence = 0.9,
                        observedAt = BIRTH_AGE_OBSERVED_AT,
                    ),
                ),
            ),
        )

        val fact = knowledgeOf(USER_BIRTH_YEAR).getValue(USER_BIRTH_YEAR).facts.single()

        assertEquals(UserFactCategory.PROFILE_ATTRIBUTE, fact.category)
        assertEquals("birth_year", fact.factKey)
        assertEquals("1990", fact.value)
        assertEquals(mapOf("year" to 1990), fact.valuePayload)
        assertEquals(UserFactStatus.CONFIRMED, fact.status)
        assertEquals(0.900, fact.confidence, CONFIDENCE_DELTA)
    }

    @Test
    fun `doesNotWriteKnowledgeTables`() {
        val factId = fixtures.insertFact(userId = USER_READ_ONLY, value = "футбол", lastConfirmedAt = SUMMARY_AT)
        fixtures.insertEvidence(factId = factId, peerId = READ_ONLY_PEER)
        val snapshotBefore = fixtures.snapshot(USER_READ_ONLY, READ_ONLY_PEER)
        val countsBefore = fixtures.counts(USER_READ_ONLY, READ_ONLY_PEER)

        val knowledge = knowledgeOf(USER_READ_ONLY)

        assertEquals(snapshotBefore, fixtures.snapshot(USER_READ_ONLY, READ_ONLY_PEER))
        assertEquals(countsBefore, fixtures.counts(USER_READ_ONLY, READ_ONLY_PEER))
        assertEquals(
            mapOf(
                "user_facts" to 1,
                "user_fact_evidence" to 1,
                "user_knowledge_summary" to 0,
                "chat_knowledge_summary" to 0,
                "knowledge_extraction_cursors" to 0,
            ),
            countsBefore,
        )
        assertEquals(1, knowledge.getValue(USER_READ_ONLY).facts.size)
    }

    private fun knowledgeOf(vararg userIds: Long): Map<Long, UserKnowledge> = findUserKnowledgePort.execute(
        FakePersistencePortProvider.createFindUserKnowledgeRequest(
            userIds = userIds.map { userId -> FakeVoProvider.createFromId(userId) },
        ),
    ).knowledge.mapKeys { (fromId, _) -> fromId.value }

    private fun clearKnowledge() {
        listOf(
            USER_WITH_DATA,
            USER_WITHOUT_DATA,
            USER_CARDS,
            USER_STALE,
            USER_FRESH_SUMMARY,
            USER_STALE_SUMMARY,
            USER_NEW_FACT_SUMMARY,
            USER_NO_SUMMARY,
            USER_BIRTH_YEAR,
        ).forEach { userId -> fixtures.deleteKnowledge(userId, userId) }
        fixtures.deleteKnowledge(USER_READ_ONLY, READ_ONLY_PEER)
    }

    private companion object {
        private const val REQUESTED_USER_COUNT = 2
    }
}
