package com.simarel.vkbot.integration

import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgeSummaryStatePort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import javax.sql.DataSource

private const val USER_WITHOUT_SUMMARY = 7_310_001L
private const val USER_WITH_SUMMARY = 7_310_002L
private const val USER_UPSERT = 7_310_003L
private const val NEWER_FACTS = 3
private const val ONE_ROW = 1
private const val COVERED_FACTS = 2
private val SUMMARY_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-01T00:00:00Z")
private val NEWER_FACT_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-02T00:00:00Z")
private val OLDER_FACT_AT: OffsetDateTime = OffsetDateTime.parse("2026-09-30T00:00:00Z")
private val HISTORICAL_FACT_AT: OffsetDateTime = OffsetDateTime.parse("2026-10-03T00:00:00Z")

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class UserKnowledgeSummaryIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var findUserKnowledgeSummaryStatePort: FindUserKnowledgeSummaryStatePort

    @Inject
    lateinit var saveUserKnowledgeSummaryPort: SaveUserKnowledgeSummaryPort

    @Inject
    lateinit var findUserKnowledgePort: FindUserKnowledgePort

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
    fun `returnsNoStateWithoutSummaryRow`() {
        fixtures.insertFact(userId = USER_WITHOUT_SUMMARY, value = "футбол", lastConfirmedAt = NEWER_FACT_AT)

        val state = stateOf(USER_WITHOUT_SUMMARY)

        assertNull(state.summarizedAt)
        assertEquals(0, state.factsSinceSummary)
    }

    @Test
    fun `countsOnlyCurrentFactsNewerThanSummary`() {
        fixtures.insertSummary(USER_WITH_SUMMARY, "сводка", SUMMARY_AT, SUMMARY_AT)
        repeat(NEWER_FACTS) { index ->
            fixtures.insertFact(
                userId = USER_WITH_SUMMARY,
                value = "новый $index",
                lastConfirmedAt = NEWER_FACT_AT,
            )
        }
        fixtures.insertFact(userId = USER_WITH_SUMMARY, value = "старый", lastConfirmedAt = OLDER_FACT_AT)
        fixtures.insertFact(userId = USER_WITH_SUMMARY, value = "на границе", lastConfirmedAt = SUMMARY_AT)
        fixtures.insertFact(
            userId = USER_WITH_SUMMARY,
            value = "исторический",
            lastConfirmedAt = HISTORICAL_FACT_AT,
            status = UserFactStatus.HISTORICAL,
            supersededAt = HISTORICAL_FACT_AT,
        )

        val state = stateOf(USER_WITH_SUMMARY)

        assertEquals(SUMMARY_AT, state.summarizedAt)
        assertEquals(NEWER_FACTS, state.factsSinceSummary)
    }

    @Test
    fun `upsertsSingleRowWithFactsCoveredAndMaxLastFactAt`() {
        fixtures.insertFact(userId = USER_UPSERT, value = "футбол", lastConfirmedAt = SUMMARY_AT)
        fixtures.insertFact(userId = USER_UPSERT, value = "история", lastConfirmedAt = NEWER_FACT_AT)
        fixtures.insertFact(
            userId = USER_UPSERT,
            value = "теннис",
            lastConfirmedAt = HISTORICAL_FACT_AT,
            status = UserFactStatus.HISTORICAL,
            supersededAt = HISTORICAL_FACT_AT,
        )

        saveUserKnowledgeSummaryPort.execute(createSaveRequest(USER_UPSERT, "первый текст"))
        saveUserKnowledgeSummaryPort.execute(createSaveRequest(USER_UPSERT, "новый текст"))

        assertEquals(ONE_ROW, fixtures.counts(USER_UPSERT, USER_UPSERT).getValue("user_knowledge_summary"))
        val row = fixtures.summaryRow(USER_UPSERT)
        assertEquals("новый текст", row["summary_text"])
        assertEquals(COVERED_FACTS, (row["facts_covered"] as Number).toInt())
        assertEquals(NEWER_FACT_AT, row["last_fact_at"])
    }

    @Test
    fun `readsSummaryAsFreshAfterSave`() {
        fixtures.insertFact(userId = USER_UPSERT, value = "футбол", lastConfirmedAt = SUMMARY_AT)
        saveUserKnowledgeSummaryPort.execute(createSaveRequest(USER_UPSERT, "новый текст"))

        val knowledge = knowledgeOf(USER_UPSERT).getValue(USER_UPSERT)

        assertEquals("новый текст", knowledge.summary)
        assertTrue(knowledge.summaryFresh)
    }

    private fun stateOf(userId: Long) = findUserKnowledgeSummaryStatePort.execute(
        FakePersistencePortProvider.createFindUserKnowledgeSummaryStateRequest(userId = userId),
    )

    private fun knowledgeOf(userId: Long): Map<Long, UserKnowledge> = findUserKnowledgePort.execute(
        FakePersistencePortProvider.createFindUserKnowledgeRequest(
            userIds = listOf(FakeVoProvider.createFromId(userId)),
        ),
    ).knowledge.mapKeys { (fromId, _) -> fromId.value }

    private fun createSaveRequest(userId: Long, summaryText: String) = FakePersistencePortProvider
        .createSaveUserKnowledgeSummaryRequest(
            userId = userId,
            summaryText = summaryText,
        )

    private fun clearKnowledge() {
        listOf(USER_WITHOUT_SUMMARY, USER_WITH_SUMMARY, USER_UPSERT).forEach { userId ->
            fixtures.deleteKnowledge(userId, userId)
        }
    }
}
