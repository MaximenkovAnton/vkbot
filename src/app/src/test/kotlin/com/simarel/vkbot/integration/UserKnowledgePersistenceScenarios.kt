package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.domain.knowledge.KnowledgeValueHash
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun UserKnowledgePersistenceFixtures.message(
    peerId: Long,
    conversationMessageId: Long,
    text: String,
    date: String,
): UUID = insertMessage(peerId, conversationMessageId, 9_100_000L, text, OffsetDateTime.parse(date))

@Suppress("LongParameterList")
fun UserKnowledgePersistenceFixtures.candidate(
    userId: Long,
    peerId: Long,
    category: UserFactCategory,
    factKey: String,
    value: String,
    sourceKind: KnowledgeSourceKind,
    confidence: Double,
    conversationMessageId: Long,
    observedAt: String,
): UserFactCandidate = candidate(
    userId,
    peerId,
    category,
    factKey,
    value,
    sourceKind,
    confidence,
    conversationMessageId,
    OffsetDateTime.parse(observedAt),
)

fun UserKnowledgePersistenceFixtures.sportCandidate(
    userId: Long,
    peerId: Long,
    value: String,
    sourceKind: KnowledgeSourceKind,
    confidence: Double,
    conversationMessageId: Long,
    observedAt: String,
): UserFactCandidate = candidate(
    userId,
    peerId,
    UserFactCategory.INTEREST,
    "interest_sport",
    value,
    sourceKind,
    confidence,
    conversationMessageId,
    observedAt,
)

fun UserKnowledgePersistenceFixtures.musicCandidate(
    userId: Long,
    peerId: Long,
    value: String,
    conversationMessageId: Long,
    observedAt: String,
): UserFactCandidate = candidate(
    userId,
    peerId,
    UserFactCategory.INTEREST,
    "interest_music",
    value,
    KnowledgeSourceKind.SELF_STATEMENT,
    0.9,
    conversationMessageId,
    observedAt,
)

fun UserKnowledgePersistenceFixtures.ageCandidate(
    userId: Long,
    peerId: Long,
    value: String,
    conversationMessageId: Long,
    observedAt: String,
): UserFactCandidate = candidate(
    userId,
    peerId,
    UserFactCategory.PROFILE_ATTRIBUTE,
    "age",
    value,
    KnowledgeSourceKind.SELF_STATEMENT,
    0.9,
    conversationMessageId,
    observedAt,
)

fun UserKnowledgePersistenceFixtures.assertCard(
    userId: Long,
    value: String,
    confidence: Double,
    status: String,
    evidenceCount: Int,
    conflict: Boolean,
    maxEvidenceConfidence: Double,
) {
    val fact = fact(userId, value)
    assertEquals(confidence, fact.asDouble("confidence"), 0.0000001)
    assertEquals(status, fact.asString("status"))
    assertEquals(evidenceCount, fact.asInt("evidence_count"))
    assertEquals(conflict, fact.asBoolean("conflict"))
    assertEquals(maxEvidenceConfidence, fact.asDouble("max_evidence_confidence"), 0.0000001)
    assertNull(fact["superseded_at"])
}

fun UserKnowledgePersistenceFixtures.assertCursor(
    peerId: Long,
    lastConversationMessageId: Long,
    completedAt: String,
    updatedAt: String,
) {
    val cursor = cursor(peerId)
    assertEquals(lastConversationMessageId, cursor.asLong("last_conversation_message_id"))
    assertEquals(OffsetDateTime.parse(completedAt).toInstant(), cursor.asTimestamp("last_completed_at").toInstant())
    assertEquals(OffsetDateTime.parse(updatedAt).toInstant(), cursor.asTimestamp("updated_at").toInstant())
}

fun UserKnowledgePersistenceFixtures.conflictWindowStart(userId: Long, peerId: Long) {
    saveSingle(
        candidate(
            userId, peerId, UserFactCategory.INTEREST, "interest_sport", "футбол",
            KnowledgeSourceKind.SELF_STATEMENT, 0.9, 201, "2026-01-01T00:00:00Z",
        ),
    )
    assertCard(userId, "футбол", 0.900, "CONFIRMED", 1, false, 0.900)
    val footballUpdatedBefore = fact(userId, "футбол").asTimestamp("updated_at")

    saveSingle(
        sportCandidate(userId, peerId, "теннис", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 202, "2026-06-01T00:00:00Z"),
    )
    assertCard(userId, "футбол", 0.750, "CANDIDATE", 1, true, 0.900)
    assertCard(userId, "теннис", 0.750, "CANDIDATE", 1, true, 0.900)
    assertTrue(fact(userId, "футбол").asTimestamp("updated_at").isAfter(footballUpdatedBefore))
}

fun UserKnowledgePersistenceFixtures.conflictWindowConfirmation(userId: Long, peerId: Long) {
    val tennisUpdatedBefore = fact(userId, "теннис").asTimestamp("updated_at")

    saveSingle(
        sportCandidate(userId, peerId, "теннис", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 203, "2026-06-02T00:00:00Z"),
    )
    assertCard(userId, "теннис", 1.000, "CONFIRMED", 2, false, 0.900)
    assertCard(userId, "футбол", 0.750, "CANDIDATE", 1, true, 0.900)
    assertTrue(fact(userId, "теннис").asTimestamp("updated_at").isAfter(tennisUpdatedBefore))

    saveSingle(
        sportCandidate(userId, peerId, "шахматы", KnowledgeSourceKind.INFERENCE, 0.6, 204, "2026-06-03T00:00:00Z"),
    )
    assertCard(userId, "шахматы", 0.450, "CANDIDATE", 1, true, 0.600)
    assertCard(userId, "теннис", 0.850, "CANDIDATE", 2, true, 0.900)

    saveSingle(
        sportCandidate(userId, peerId, "шахматы", KnowledgeSourceKind.INFERENCE, 0.1, 205, "2026-06-04T00:00:00Z"),
    )
    assertCard(userId, "шахматы", 0.550, "CANDIDATE", 2, true, 0.600)
}

fun UserKnowledgePersistenceFixtures.revivalOlderStep(userId: Long, peerId: Long) {
    saveSingle(
        sportCandidate(userId, peerId, "футбол", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 701, "2026-01-01T00:00:00Z"),
    )
    saveSingle(
        sportCandidate(userId, peerId, "теннис", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 702, "2026-08-01T00:00:00Z"),
    )
    assertEquals("HISTORICAL", fact(userId, "футбол").asString("status"))

    saveSingle(
        sportCandidate(userId, peerId, "футбол", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 703, "2026-01-01T00:00:00Z"),
    )
    val older = fact(userId, "футбол")
    assertEquals("HISTORICAL", older.asString("status"))
    assertEquals(2, older.asInt("evidence_count"))
    assertEquals(1.000, older.asDouble("confidence"), 0.0000001)
    assertEquals(
        OffsetDateTime.parse("2026-08-01T00:00:00Z").toInstant(),
        older.asTimestamp("superseded_at").toInstant(),
    )
}

fun UserKnowledgePersistenceFixtures.revivalStep(userId: Long, peerId: Long) {
    saveSingle(
        sportCandidate(userId, peerId, "футбол", KnowledgeSourceKind.SELF_STATEMENT, 0.9, 704, "2026-09-01T00:00:00Z"),
    )
    val revived = fact(userId, "футбол")
    assertNull(revived["superseded_at"])
    assertEquals("CONFIRMED", revived.asString("status"))
    assertEquals(3, revived.asInt("evidence_count"))
    assertEquals(1.000, revived.asDouble("confidence"), 0.0000001)
    assertEquals(KnowledgeValueHash.of("футбол"), revived.asString("value_hash"))
    val tennis = fact(userId, "теннис")
    assertEquals("HISTORICAL", tennis.asString("status"))
    assertEquals(
        OffsetDateTime.parse("2026-09-01T00:00:00Z").toInstant(),
        tennis.asTimestamp("superseded_at").toInstant(),
    )
}

fun UserKnowledgePersistenceFixtures.repeatedDeliveryKeepsSecondCardStable(userId: Long, peerId: Long) {
    val second = candidate(
        userId, peerId, UserFactCategory.HABIT, "habit_jog", "бег",
        KnowledgeSourceKind.SELF_STATEMENT, 0.9, 201, "2026-06-02T00:00:00Z",
    )
    assertEquals(1, saveSingle(second).saved)
    assertCard(userId, "бег", 0.900, "CONFIRMED", 1, false, 0.900)
    val repeated = saveSingle(second)
    assertEquals(0, repeated.saved)
    assertEquals(1, repeated.skipped)
    assertEquals(1, fact(userId, "бег").asInt("evidence_count"))
    assertEquals(0.900, fact(userId, "бег").asDouble("confidence"), 0.0000001)
    assertFalse(fact(userId, "бег").asBoolean("conflict"))
}

fun UserKnowledgePersistenceFixtures.pendingBatchThresholds() {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    insertCursor(1_002, 39, now)
    message(1_002, 40, "m40", "2026-01-01T10:00:00Z")
    message(1_002, 41, "m41", "2026-01-01T11:00:00Z")
    message(1_002, 42, "m42", "2026-01-01T12:00:00Z")
    insertCursor(1_003, 49, now.minusMinutes(31))
    message(1_003, 50, "m50", "2026-01-01T10:00:00Z")
    insertCursor(1_004, 59, now.minusMinutes(15))
    message(1_004, 60, "m60", "2026-01-01T10:00:00Z")
    insertCursor(1_005, 69, now.minusMinutes(31))
    message(1_006, 70, "m70", "2026-01-01T10:00:00Z")
    message(1_006, 71, "m71", "2026-01-01T11:00:00Z")

    assertTrue(hasPending(1_002, minCount = 3))
    assertFalse(hasPending(1_002, minCount = 4))
    assertTrue(hasPending(1_003, minCount = 100))
    assertFalse(hasPending(1_004, minCount = 100))
    assertFalse(hasPending(1_005, minCount = 1))
    assertTrue(hasPending(1_006, minCount = 100))
    assertFalse(hasPending(1_007, minCount = 1))
    assertEquals(emptyList<Long>(), pendingIds(1_007, upToConversationMessageId = 100, limit = 10))
}
