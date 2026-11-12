package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.config.KnowledgeConfig
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@TestProfile(ProfileStorageTestProfile::class)
class UserKnowledgePersistenceIntegrationTest {

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var saveUserFactsPort: SaveUserFactsPort

    @Inject
    lateinit var hasPendingKnowledgeBatchPort: HasPendingKnowledgeBatchPort

    @Inject
    lateinit var findPendingKnowledgeBatchPort: FindPendingKnowledgeBatchPort

    @Inject
    lateinit var advanceExtractionCursorPort: AdvanceExtractionCursorPort

    @Inject
    lateinit var saveUserKnowledgeSummaryPort: SaveUserKnowledgeSummaryPort

    @Inject
    lateinit var saveChatKnowledgeSummaryPort: SaveChatKnowledgeSummaryPort

    @Inject
    lateinit var knowledgeConfig: KnowledgeConfig

    private lateinit var fixtures: UserKnowledgePersistenceFixtures

    @BeforeEach
    fun setUp() {
        fixtures = UserKnowledgePersistenceFixtures(
            dataSource,
            saveUserFactsPort,
            hasPendingKnowledgeBatchPort,
            findPendingKnowledgeBatchPort,
            advanceExtractionCursorPort,
            saveUserKnowledgeSummaryPort,
            saveChatKnowledgeSummaryPort,
        )
    }

    @Test
    fun `savesFactAndEvidence`() {
        val userId = 2_000_000_101L
        val peerId = 2_000_000_101L
        val selfMessage = fixtures.message(peerId, 101, "люблю футбол", "2026-06-01T00:00:00Z")
        val otherMessage = fixtures.message(peerId, 102, "бегаю утром", "2026-06-02T00:00:00Z")

        val response = fixtures.save(
            listOf(
                fixtures.candidate(
                    userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
                    KnowledgeSourceKind.SELF_STATEMENT, 0.9, 101, "2026-06-01T00:00:00Z",
                ),
                fixtures.candidate(
                    userId, peerId, UserFactCategory.HABIT, "habit_morning", "бег",
                    KnowledgeSourceKind.OTHER_STATEMENT, 0.8, 102, "2026-06-02T00:00:00Z",
                ),
            ),
        )

        assertEquals(2, response.saved)
        assertEquals(0, response.skipped)
        assertEquals(2, fixtures.facts(userId).size)
        assertEquals(2, fixtures.evidence(userId).size)
        fixtures.assertCard(userId, "футбол", 0.900, "CONFIRMED", 1, false, 0.900)
        fixtures.assertCard(userId, "бег", 0.800, "CONFIRMED", 1, false, 0.800)
        assertEquals(peerId, fixtures.fact(userId, "футбол").asLong("peer_id"))
        val evidence = fixtures.evidence(userId)
        assertEquals(selfMessage, evidence.single { it.asString("source_kind") == "SELF_STATEMENT" }["message_id"])
        assertEquals(otherMessage, evidence.single { it.asString("source_kind") == "OTHER_STATEMENT" }["message_id"])
    }

    @Test
    fun `repeatedDeliveryIsNoOp`() {
        val userId = 2_000_000_102L
        val peerId = 2_000_000_102L
        fixtures.message(peerId, 111, "футбол", "2026-06-01T00:00:00Z")
        val candidate = fixtures.candidate(
            userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
            KnowledgeSourceKind.SELF_STATEMENT, 0.9, 111, "2026-06-01T00:00:00Z",
        )

        val first = fixtures.saveSingle(candidate)
        assertEquals(1, first.saved)
        assertEquals(0, first.skipped)
        val updatedBefore = fixtures.fact(userId, "футбол").asTimestamp("updated_at")

        val second = fixtures.saveSingle(candidate)

        assertEquals(0, second.saved)
        assertEquals(1, second.skipped)
        assertEquals(1, fixtures.facts(userId).size)
        assertEquals(1, fixtures.evidence(userId).size)
        fixtures.assertCard(userId, "футбол", 0.900, "CONFIRMED", 1, false, 0.900)
        assertEquals(updatedBefore.toInstant(), fixtures.fact(userId, "футбол").asTimestamp("updated_at").toInstant())
    }

    @Test
    fun `conflictWithinWindowAndConfirmationClears`() {
        val userId = 2_000_000_201L
        val peerId = 2_000_000_201L
        listOf(
            201 to "2026-01-01T00:00:00Z",
            202 to "2026-06-01T00:00:00Z",
            203 to "2026-06-02T00:00:00Z",
            204 to "2026-06-03T00:00:00Z",
            205 to "2026-06-04T00:00:00Z",
        ).forEach { (conversationMessageId, date) ->
            fixtures.message(peerId, conversationMessageId.toLong(), "m", date)
        }

        fixtures.conflictWindowStart(userId, peerId)
        fixtures.conflictWindowConfirmation(userId, peerId)
    }

    @Test
    fun `changeBeyondWindowSupersedes`() {
        val userId = 2_000_000_301L
        val peerId = 2_000_000_301L
        fixtures.message(peerId, 301, "рок", "2026-01-01T00:00:00Z")
        fixtures.message(peerId, 302, "джаз", "2026-07-15T00:00:00Z")

        fixtures.saveSingle(fixtures.musicCandidate(userId, peerId, "рок", 301, "2026-01-01T00:00:00Z"))
        fixtures.assertCard(userId, "рок", 0.900, "CONFIRMED", 1, false, 0.900)
        val rockUpdatedBefore = fixtures.fact(userId, "рок").asTimestamp("updated_at")

        fixtures.saveSingle(fixtures.musicCandidate(userId, peerId, "джаз", 302, "2026-07-15T00:00:00Z"))

        fixtures.assertCard(userId, "джаз", 0.900, "CONFIRMED", 1, false, 0.900)
        val rock = fixtures.fact(userId, "рок")
        assertEquals("HISTORICAL", rock.asString("status"))
        assertTrue(rock.asTimestamp("updated_at").isAfter(rockUpdatedBefore))
        assertEquals(
            OffsetDateTime.parse("2026-07-15T00:00:00Z").toInstant(),
            rock.asTimestamp("superseded_at").toInstant(),
        )
    }

    @Test
    fun `repeatedEvidenceKeepsCountAndTwentyRefs`() {
        val userId = 2_000_000_401L
        val peerId = 2_000_000_401L
        val candidates = (101L..121L).map { conversationMessageId ->
            val observedAt = "2026-06-01T%02d:00:00Z".format(conversationMessageId - 101)
            fixtures.message(peerId, conversationMessageId, "футбол", observedAt)
            val sourceKind = if (conversationMessageId == 101L) {
                KnowledgeSourceKind.SELF_STATEMENT
            } else {
                KnowledgeSourceKind.INFERENCE
            }
            val confidence = if (conversationMessageId == 101L) 0.9 else 0.1
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
                sourceKind, confidence, conversationMessageId, observedAt,
            )
        }

        fixtures.save(candidates)

        val fact = fixtures.fact(userId, "футбол")
        assertEquals(21, fact.asInt("evidence_count"))
        assertEquals(1.000, fact.asDouble("confidence"), DELTA)
        assertEquals(0.900, fact.asDouble("max_evidence_confidence"), DELTA)
        assertEquals(20, fixtures.evidence(userId).size)
        assertFalse(fixtures.evidence(userId).any { it.asLong("conversation_message_id") == 101L })

        assertEquals(0, fixtures.saveSingle(candidates.last()).saved)
        assertEquals(21, fixtures.fact(userId, "футбол").asInt("evidence_count"))

        fixtures.repeatedDeliveryKeepsSecondCardStable(userId, peerId)
    }

    @Test
    fun `concurrentEvidenceKeepsAtomicAggregate`() {
        val userId = 2_000_000_402L
        val peerId = 2_000_000_402L
        fixtures.saveSingle(
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 201, "2026-06-01T00:00:00Z",
            ),
        )
        val candidates = (202L..209L).map { conversationMessageId ->
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
                KnowledgeSourceKind.INFERENCE, 0.1, conversationMessageId, "2026-06-02T00:00:00Z",
            )
        }
        val executor = Executors.newFixedThreadPool(candidates.size)
        try {
            val start = CountDownLatch(1)
            val futures = candidates.map { candidate ->
                executor.submit {
                    start.await()
                    fixtures.saveSingle(candidate)
                }
            }
            start.countDown()
            futures.forEach { future -> future.get(THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        val fact = fixtures.fact(userId, "футбол")
        assertEquals(9, fact.asInt("evidence_count"))
        assertEquals(0.900, fact.asDouble("max_evidence_confidence"), DELTA)
        assertEquals(1.000, fact.asDouble("confidence"), DELTA)
    }

    @Test
    fun `oneCardPerValueAcrossChats`() {
        val userId = 2_000_000_501L
        fixtures.message(111, 401, "футбол", "2026-06-01T00:00:00Z")
        fixtures.message(222, 402, "футбол", "2026-06-02T00:00:00Z")

        fixtures.saveSingle(
            fixtures.candidate(
                userId, 111, UserFactCategory.INTEREST, "interest_sport", "футбол",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 401, "2026-06-01T00:00:00Z",
            ),
        )
        fixtures.saveSingle(
            fixtures.candidate(
                userId, 222, UserFactCategory.INTEREST, "interest_sport", "футбол",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 402, "2026-06-02T00:00:00Z",
            ),
        )

        val fact = fixtures.facts(userId).single()
        assertEquals(111L, fact.asLong("peer_id"))
        assertEquals(2, fact.asInt("evidence_count"))
        assertEquals(setOf(111L, 222L), fixtures.evidence(userId).map { it.asLong("peer_id") }.toSet())
    }

    @Test
    fun `revivesHistoricalValue`() {
        val userId = 2_000_000_701L
        val peerId = 2_000_000_701L
        listOf(
            701 to "2026-01-01T00:00:00Z",
            702 to "2026-08-01T00:00:00Z",
            703 to "2026-01-01T00:00:00Z",
            704 to "2026-09-01T00:00:00Z",
        ).forEach { (conversationMessageId, date) ->
            fixtures.message(peerId, conversationMessageId.toLong(), "m", date)
        }

        fixtures.revivalOlderStep(userId, peerId)
        fixtures.revivalStep(userId, peerId)
    }

    @Test
    fun `ageStatementsShareBirthYearCard`() {
        val userId = 2_000_000_851L
        val peerId = 2_000_000_851L
        fixtures.saveSingle(fixtures.ageCandidate(userId, peerId, "мне 20", 851, "2010-06-01T00:00:00Z"))
        fixtures.assertCard(userId, "1990", 0.900, "CONFIRMED", 1, false, 0.900)
        assertEquals("birth_year", fixtures.fact(userId, "1990").asString("fact_key"))

        fixtures.saveSingle(fixtures.ageCandidate(userId, peerId, "мне 30", 852, "2011-06-01T00:00:00Z"))

        assertEquals(2, fixtures.facts(userId).size)
        val current = fixtures.facts(userId).filter { it.asString("status") != "HISTORICAL" }
        assertEquals(1, current.size)
        assertEquals("1981", current.single().asString("value"))
        assertEquals("birth_year", current.single().asString("fact_key"))
        val earlier = fixtures.fact(userId, "1990")
        assertEquals("HISTORICAL", earlier.asString("status"))
        assertEquals(
            OffsetDateTime.parse("2011-06-01T00:00:00Z").toInstant(),
            earlier.asTimestamp("superseded_at").toInstant(),
        )
    }

    @Test
    fun `olderWithoutCardCreatesHistorical`() {
        val userId = 2_000_000_801L
        val peerId = 2_000_000_801L
        fixtures.message(peerId, 705, "футбол", "2026-01-01T00:00:00Z")
        fixtures.message(peerId, 706, "хоккей", "2026-08-01T00:00:00Z")
        fixtures.message(peerId, 707, "плавание", "2026-01-01T00:00:00Z")

        fixtures.saveSingle(
            fixtures.sportCandidate(
                userId,
                peerId,
                "футбол",
                KnowledgeSourceKind.SELF_STATEMENT,
                0.9,
                705,
                "2026-01-01T00:00:00Z",
            ),
        )
        fixtures.saveSingle(
            fixtures.sportCandidate(
                userId,
                peerId,
                "хоккей",
                KnowledgeSourceKind.SELF_STATEMENT,
                0.9,
                706,
                "2026-08-01T00:00:00Z",
            ),
        )
        fixtures.saveSingle(
            fixtures.sportCandidate(
                userId,
                peerId,
                "плавание",
                KnowledgeSourceKind.SELF_STATEMENT,
                0.9,
                707,
                "2026-01-01T00:00:00Z",
            ),
        )

        assertEquals(3, fixtures.facts(userId).size)
        val swimming = fixtures.fact(userId, "плавание")
        assertEquals("HISTORICAL", swimming.asString("status"))
        assertEquals(0.900, swimming.asDouble("confidence"), DELTA)
        assertEquals(1, swimming.asInt("evidence_count"))
        assertEquals(0.900, swimming.asDouble("max_evidence_confidence"), DELTA)
        assertEquals(
            OffsetDateTime.parse("2026-08-01T00:00:00Z").toInstant(),
            swimming.asTimestamp("superseded_at").toInstant(),
        )
        fixtures.assertCard(userId, "хоккей", 0.900, "CONFIRMED", 1, false, 0.900)
    }

    @Test
    fun `knowledgeConfigUsesDeclaredDefaults`() {
        assertEquals(0.8, knowledgeConfig.confirmThreshold, DELTA)
        assertEquals(20, knowledgeConfig.evidenceRefsLimit)
        assertEquals(
            0.8,
            ConfigProvider.getConfig().getValue("knowledge.confirm-threshold", Double::class.java),
            DELTA,
        )
        assertEquals(20, ConfigProvider.getConfig().getValue("knowledge.evidence-refs-limit", Int::class.java))

        val resources = javaClass.classLoader.getResources("application.yml").toList().map { it.readText() }
        assertTrue(
            resources.any { text ->
                text.contains("confirm-threshold: \${KNOWLEDGE_CONFIRM_THRESHOLD:0.8}") &&
                    text.contains("evidence-refs-limit: \${KNOWLEDGE_EVIDENCE_REFS_LIMIT:20}")
            },
        )
    }

    @Test
    fun `pendingBatchFollowsCursorAndThresholds`() {
        val peerId = 1_001L
        fixtures.message(peerId, 10, "m10", "2026-01-01T10:00:00Z")
        fixtures.message(peerId, 20, "m20", "2026-01-01T11:00:00Z")
        fixtures.message(peerId, 30, "m30", "2026-01-01T12:00:00Z")

        assertEquals(listOf(10L, 20L), fixtures.pendingIds(peerId, upToConversationMessageId = 20, limit = 5))
        assertEquals(listOf(10L, 20L), fixtures.pendingIds(peerId, upToConversationMessageId = 30, limit = 2))

        fixtures.advance(peerId, 20, "2026-11-01T11:00:00Z")
        fixtures.assertCursor(peerId, 20, "2026-11-01T11:00:00Z", "2026-11-01T11:00:00Z")
        assertEquals(listOf(30L), fixtures.pendingIds(peerId, upToConversationMessageId = 30, limit = 5))

        fixtures.advance(peerId, 25, "2026-11-01T12:00:00Z")
        fixtures.assertCursor(peerId, 25, "2026-11-01T12:00:00Z", "2026-11-01T12:00:00Z")

        fixtures.advance(peerId, 15, "2026-11-01T10:30:00Z")
        fixtures.assertCursor(peerId, 25, "2026-11-01T12:00:00Z", "2026-11-01T10:30:00Z")
        assertEquals(listOf(30L), fixtures.pendingIds(peerId, upToConversationMessageId = 30, limit = 5))

        fixtures.advance(peerId, 30, "2026-11-01T13:00:00Z")
        fixtures.assertCursor(peerId, 30, "2026-11-01T13:00:00Z", "2026-11-01T13:00:00Z")
        assertEquals(emptyList<Long>(), fixtures.pendingIds(peerId, upToConversationMessageId = 30, limit = 5))

        fixtures.pendingBatchThresholds()
    }

    @Test
    fun `upsertsKnowledgeSummaries`() {
        val userId = 2_000_000_901L
        fixtures.insertFact(userId, "a", UserFactStatus.CONFIRMED, OffsetDateTime.parse("2025-08-01T00:00:00Z"))
        fixtures.insertFact(userId, "b", UserFactStatus.CONFIRMED, OffsetDateTime.parse("2025-08-02T00:00:00Z"))
        fixtures.insertFact(userId, "c", UserFactStatus.CONFIRMED, OffsetDateTime.parse("2025-08-03T00:00:00Z"))
        fixtures.insertFact(userId, "d", UserFactStatus.HISTORICAL, OffsetDateTime.parse("2025-07-01T00:00:00Z"))
        val startedAt = OffsetDateTime.now(ZoneOffset.UTC)

        fixtures.saveUserSummary(userId, "first")
        val summary = fixtures.userSummary(userId)
        assertEquals(3, summary.asInt("facts_covered"))
        assertEquals(
            OffsetDateTime.parse("2025-08-03T00:00:00Z").toInstant(),
            summary.asTimestamp("last_fact_at").toInstant(),
        )
        assertFalse(summary.asTimestamp("summarized_at").isBefore(startedAt))

        fixtures.saveUserSummary(userId, "second")
        assertEquals("second", fixtures.userSummary(userId).asString("summary_text"))

        val emptyUser = 2_000_000_902L
        fixtures.saveUserSummary(emptyUser, "empty")
        val emptySummary = fixtures.userSummary(emptyUser)
        assertEquals(0, emptySummary.asInt("facts_covered"))
        assertEquals(
            emptySummary.asTimestamp("summarized_at").toInstant(),
            emptySummary.asTimestamp("last_fact_at").toInstant(),
        )

        val chatPeerId = 2_000_000_903L
        fixtures.saveChatSummary(chatPeerId, "chat one", 10, 70, 7)
        val chat = fixtures.chatSummary(chatPeerId)
        assertEquals(7, chat.asInt("messages_covered"))
        assertEquals(10L, chat.asLong("first_conversation_message_id"))
        assertEquals(70L, chat.asLong("last_conversation_message_id"))
        fixtures.saveChatSummary(chatPeerId, "chat two", 11, 71, 8)
        assertEquals("chat two", fixtures.chatSummary(chatPeerId).asString("summary_text"))
        assertEquals(
            1,
            fixtures.count(
                "select count(*) as total from chat_knowledge_summary where peer_id = ?",
                chatPeerId,
            ),
        )
    }

    @Test
    fun `skipsInvalidCandidatesWithoutWriting`() {
        val userId = 2_000_000_951L
        val peerId = 2_000_000_951L
        val invalid = listOf(
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_sport", "   ",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 901, "2026-01-01T00:00:00Z",
            ),
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_sport",
                "x".repeat(UserFactCandidate.MAX_VALUE_LENGTH + 1),
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 902, "2026-01-01T00:00:00Z",
            ),
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "Jo hn", "футбол",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 903, "2026-01-01T00:00:00Z",
            ),
        )

        val response = fixtures.save(invalid)
        assertEquals(0, response.saved)
        assertEquals(3, response.skipped)
        assertEquals(0, fixtures.facts(userId).size)
        assertEquals(0, fixtures.evidence(userId).size)

        val valid = fixtures.candidate(
            userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
            KnowledgeSourceKind.SELF_STATEMENT, 0.9, 904, "2026-01-01T00:00:00Z",
        )
        val mixed = fixtures.save(listOf(invalid.first(), valid))
        assertEquals(1, mixed.saved)
        assertEquals(1, mixed.skipped)
        fixtures.assertCard(userId, "футбол", 0.900, "CONFIRMED", 1, false, 0.900)
    }

    @Test
    fun `storesNoMessageTextInKnowledgeTables`() {
        val userId = 2_000_000_961L
        val peerId = 2_000_000_961L
        val marker = "секрет-строка-42"
        val messageId = fixtures.message(peerId, 961, marker, "2026-06-01T00:00:00Z")
        fixtures.saveSingle(
            fixtures.candidate(
                userId, peerId, UserFactCategory.INTEREST, "interest_secret", "увлечение",
                KnowledgeSourceKind.SELF_STATEMENT, 0.9, 961, "2026-06-01T00:00:00Z",
            ),
        )

        listOf(
            "user_facts",
            "user_fact_evidence",
            "user_knowledge_summary",
            "chat_knowledge_summary",
            "knowledge_extraction_cursors",
        ).forEach { table -> assertEquals(0, fixtures.tableTextOccurrences(table, marker)) }
        assertEquals(
            0,
            fixtures.count(
                "select count(*) as total from information_schema.columns " +
                    "where table_name = 'user_fact_evidence' and column_name in ('message_text', 'text')",
            ),
        )
        assertEquals(messageId, fixtures.evidence(userId).single()["message_id"])
        assertNull(fixtures.fact(userId, "увлечение")["superseded_at"])
    }

    private companion object {
        private const val DELTA = 0.0000001
        private const val THREAD_TIMEOUT_SECONDS = 30L
    }
}
