@file:Suppress("VariableNaming")

package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.KnowledgeExtractionCursors
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.Messages
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserFactEvidence
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserFacts
import com.simarel.vkbot.persistence.domain.knowledge.FactConfidenceCalculator
import com.simarel.vkbot.persistence.domain.knowledge.FactTransition
import com.simarel.vkbot.persistence.domain.knowledge.KnowledgeTemporalPolicy
import com.simarel.vkbot.persistence.domain.knowledge.KnowledgeValueHash
import com.simarel.vkbot.persistence.domain.knowledge.NormalizedFact
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.Duration
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Suppress("TooManyFunctions")
@ApplicationScoped
open class UserKnowledgeTable {

    @Inject
    lateinit var dsl: DSLContext

    @Inject
    lateinit var objectMapper: ObjectMapper

    open fun hasPendingBatch(peerId: Long, minCount: Int, minInterval: Duration): Boolean {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val cursor = readCursor(peerId)
        val countLimit = maxOf(minCount, MIN_PENDING_COUNT)
        val condition = if (cursor == null) {
            Messages.PEER_ID.eq(peerId)
        } else {
            Messages.PEER_ID.eq(peerId)
                .and(Messages.CONVERSATION_MESSAGE_ID.gt(cursor.lastConversationMessageId))
        }
        val pendingCount = dsl.fetchCount(
            dsl.selectOne()
                .from(Messages.TABLE)
                .where(condition)
                .limit(countLimit)
                .asTable("pending"),
        )
        val intervalExpired = cursor == null || cursor.lastCompletedAt <= now.minus(minInterval)
        return pendingCount >= minCount || (pendingCount >= MIN_PENDING_COUNT && intervalExpired)
    }

    open fun findPendingMessages(peerId: Long, upToConversationMessageId: Long, limit: Int): List<StoredMessage> {
        if (limit <= 0) {
            return emptyList()
        }
        val cursor = readCursor(peerId)
        val condition = if (cursor == null) {
            Messages.PEER_ID.eq(peerId)
        } else {
            Messages.PEER_ID.eq(peerId)
                .and(Messages.CONVERSATION_MESSAGE_ID.gt(cursor.lastConversationMessageId))
        }
        return dsl.select(
            Messages.ID,
            Messages.PEER_ID,
            Messages.CONVERSATION_MESSAGE_ID,
            Messages.FROM_ID,
            Messages.MESSAGE_TEXT,
            Messages.DATE,
            Messages.FORWARDED_MESSAGES,
        )
            .from(Messages.TABLE)
            .where(condition)
            .and(Messages.CONVERSATION_MESSAGE_ID.le(upToConversationMessageId))
            .orderBy(Messages.CONVERSATION_MESSAGE_ID.asc())
            .limit(limit)
            .fetch()
            .map { record -> record.toStoredMessage() }
    }

    open fun findFactsByUserIds(userIds: List<Long>): List<UserFact> {
        if (userIds.isEmpty()) {
            return emptyList()
        }
        return dsl.select(
            UserFacts.ID,
            UserFacts.USER_ID,
            UserFacts.PEER_ID,
            UserFacts.CATEGORY,
            UserFacts.FACT_KEY,
            UserFacts.VALUE,
            UserFacts.VALUE_PAYLOAD,
            UserFacts.CONFIDENCE,
            UserFacts.STATUS,
            UserFacts.EVIDENCE_COUNT,
            UserFacts.FIRST_OBSERVED_AT,
            UserFacts.LAST_CONFIRMED_AT,
            UserFacts.SUPERSEDED_AT,
            UserFacts.CONFLICT,
        )
            .from(UserFacts.TABLE)
            .where(UserFacts.USER_ID.`in`(userIds))
            .orderBy(
                UserFacts.CATEGORY.asc(),
                UserFacts.FACT_KEY.asc(),
                UserFacts.LAST_CONFIRMED_AT.desc(),
                UserFacts.VALUE.asc(),
            )
            .fetch()
            .map { record -> record.toUserFact(objectMapper) }
    }

    open fun findCurrentBirthdayFacts(monthDay: MonthDay): List<UserFact> = dsl.select(
        UserFacts.ID,
        UserFacts.USER_ID,
        UserFacts.PEER_ID,
        UserFacts.CATEGORY,
        UserFacts.FACT_KEY,
        UserFacts.VALUE,
        UserFacts.VALUE_PAYLOAD,
        UserFacts.CONFIDENCE,
        UserFacts.STATUS,
        UserFacts.EVIDENCE_COUNT,
        UserFacts.FIRST_OBSERVED_AT,
        UserFacts.LAST_CONFIRMED_AT,
        UserFacts.SUPERSEDED_AT,
        UserFacts.CONFLICT,
    )
        .from(UserFacts.TABLE)
        .where(UserFacts.CATEGORY.eq(UserFactCategory.PROFILE_ATTRIBUTE.name))
        .and(UserFacts.FACT_KEY.eq(BIRTHDAY_FACT_KEY))
        .and(UserFacts.STATUS.ne(UserFactStatus.HISTORICAL.name))
        .and(BIRTHDAY_MONTH_DAY_FIELD.eq(birthdayMonthDay(monthDay)))
        .orderBy(UserFacts.USER_ID.asc())
        .fetch()
        .map { record -> record.toUserFact(objectMapper) }

    open fun saveFact(
        candidate: UserFactCandidate,
        threshold: Double,
        evidenceRefsLimit: Int,
        now: OffsetDateTime,
    ): Boolean {
        val normalized = KnowledgeTemporalPolicy.normalize(
            candidate.category,
            candidate.factKey,
            candidate.value,
            candidate.observedAt,
        )
        val cards = findCards(
            candidate.userId,
            candidate.category,
            normalized.factKey,
            KnowledgeValueHash.of(normalized.value),
        )
        val prepared = prepareFact(candidate, normalized, cards, threshold)
        val latestCurrent = cards.latestCurrent
        val factId = upsertCard(candidate, prepared, now) ?: return false
        if (!insertEvidence(candidate, factId, prepared.newConfidence)) {
            return false
        }
        if (latestCurrent != null &&
            (prepared.transition == FactTransition.REVIVAL || prepared.transition == FactTransition.CHANGE)
        ) {
            applyCardState(
                revive = prepared.transition == FactTransition.REVIVAL,
                targetId = factId,
                currentId = latestCurrent.id,
                observedAt = candidate.observedAt,
                now = now,
            )
        }
        applyAggregatesAndTrim(
            factId,
            prepared.newConfidence,
            candidate.observedAt,
            threshold,
            evidenceRefsLimit,
            now,
        )
        if (prepared.transition == FactTransition.CONFLICT && latestCurrent != null) {
            applyConflict(listOf(factId, latestCurrent.id).distinct(), now)
        }
        return true
    }

    open fun advanceCursor(peerId: Long, lastConversationMessageId: Long, completedAt: OffsetDateTime) {
        dsl.insertInto(KnowledgeExtractionCursors.TABLE)
            .columns(
                KnowledgeExtractionCursors.PEER_ID,
                KnowledgeExtractionCursors.LAST_CONVERSATION_MESSAGE_ID,
                KnowledgeExtractionCursors.LAST_COMPLETED_AT,
                KnowledgeExtractionCursors.UPDATED_AT,
            )
            .values(peerId, lastConversationMessageId, completedAt, completedAt)
            .onConflict(KnowledgeExtractionCursors.PEER_ID)
            .doUpdate()
            .set(
                KnowledgeExtractionCursors.LAST_CONVERSATION_MESSAGE_ID,
                DSL.field(CURSOR_GREATEST_ID_SQL, SQLDataType.BIGINT),
            )
            .set(
                KnowledgeExtractionCursors.LAST_COMPLETED_AT,
                DSL.field(CURSOR_GREATEST_COMPLETED_SQL, SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .set(
                KnowledgeExtractionCursors.UPDATED_AT,
                DSL.field("excluded.updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .execute()
    }

    private fun readCursor(peerId: Long): CursorRow? = dsl.select(
        KnowledgeExtractionCursors.LAST_CONVERSATION_MESSAGE_ID,
        KnowledgeExtractionCursors.LAST_COMPLETED_AT,
    )
        .from(KnowledgeExtractionCursors.TABLE)
        .where(KnowledgeExtractionCursors.PEER_ID.eq(peerId))
        .fetchOne()
        ?.let { record ->
            CursorRow(
                lastConversationMessageId = record.get(
                    KnowledgeExtractionCursors.LAST_CONVERSATION_MESSAGE_ID,
                    Long::class.java,
                ),
                lastCompletedAt = record.get(KnowledgeExtractionCursors.LAST_COMPLETED_AT, OffsetDateTime::class.java),
            )
        }

    private fun findCards(
        userId: Long,
        category: UserFactCategory,
        factKey: String,
        valueHash: String,
    ): CardState {
        val latestCurrent = dsl.select()
            .from(UserFacts.TABLE)
            .where(UserFacts.USER_ID.eq(userId))
            .and(UserFacts.CATEGORY.eq(category.name))
            .and(UserFacts.FACT_KEY.eq(factKey))
            .and(UserFacts.STATUS.ne(UserFactStatus.HISTORICAL.name))
            .orderBy(UserFacts.LAST_CONFIRMED_AT.desc(), UserFacts.CREATED_AT.desc(), UserFacts.VALUE_HASH.asc())
            .limit(1)
            .fetchOne()
            ?.toUserFact(objectMapper)
        val historicalSameValue = dsl.select()
            .from(UserFacts.TABLE)
            .where(UserFacts.USER_ID.eq(userId))
            .and(UserFacts.CATEGORY.eq(category.name))
            .and(UserFacts.FACT_KEY.eq(factKey))
            .and(UserFacts.VALUE_HASH.eq(valueHash))
            .and(UserFacts.STATUS.eq(UserFactStatus.HISTORICAL.name))
            .orderBy(UserFacts.LAST_CONFIRMED_AT.desc(), UserFacts.VALUE_HASH.asc())
            .limit(1)
            .fetchOne()
            ?.toUserFact(objectMapper)
        return CardState(latestCurrent = latestCurrent, historicalSameValue = historicalSameValue)
    }

    private fun upsertCard(candidate: UserFactCandidate, prepared: PreparedFact, now: OffsetDateTime): UUID? {
        val insert = dsl.insertInto(UserFacts.TABLE)
            .columns(
                UserFacts.ID,
                UserFacts.USER_ID,
                UserFacts.PEER_ID,
                UserFacts.CATEGORY,
                UserFacts.FACT_KEY,
                UserFacts.VALUE,
                UserFacts.VALUE_HASH,
                UserFacts.VALUE_PAYLOAD,
                UserFacts.EVIDENCE_COUNT,
                UserFacts.MAX_EVIDENCE_CONFIDENCE,
                UserFacts.CONFIDENCE,
                UserFacts.STATUS,
                UserFacts.FIRST_OBSERVED_AT,
                UserFacts.LAST_CONFIRMED_AT,
                UserFacts.SUPERSEDED_AT,
                UserFacts.CONFLICT,
                UserFacts.CREATED_AT,
                UserFacts.UPDATED_AT,
            )
            .values(
                UUID.randomUUID(),
                candidate.userId,
                candidate.peerId,
                candidate.category.name,
                prepared.normalized.factKey,
                prepared.normalized.value,
                prepared.valueHash,
                toJsonb(objectMapper, prepared.payload),
                INITIAL_EVIDENCE_COUNT,
                BigDecimal.ZERO,
                prepared.newConfidence,
                prepared.initialStatus.name,
                candidate.observedAt,
                candidate.observedAt,
                prepared.initialSupersededAt,
                false,
                now,
                now,
            )
            .onConflict(UserFacts.USER_ID, UserFacts.CATEGORY, UserFacts.FACT_KEY, UserFacts.VALUE_HASH)
            .doUpdate()
        val payloadUpdate = if (prepared.mergePayload) {
            insert.set(UserFacts.VALUE_PAYLOAD, DSL.field("excluded.value_payload", SQLDataType.JSONB))
        } else {
            insert.set(UserFacts.VALUE_PAYLOAD, DSL.field("user_facts.value_payload", SQLDataType.JSONB))
        }
        return payloadUpdate
            .set(UserFacts.UPDATED_AT, DSL.field("user_facts.updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE))
            .returning(UserFacts.ID)
            .fetchOne(UserFacts.ID)
    }

    private fun insertEvidence(candidate: UserFactCandidate, factId: UUID, confidence: BigDecimal): Boolean {
        val inserted = dsl.insertInto(UserFactEvidence.TABLE)
            .columns(
                UserFactEvidence.ID,
                UserFactEvidence.FACT_ID,
                UserFactEvidence.MESSAGE_ID,
                UserFactEvidence.CONVERSATION_MESSAGE_ID,
                UserFactEvidence.PEER_ID,
                UserFactEvidence.SAID_BY_ID,
                UserFactEvidence.SOURCE_KIND,
                UserFactEvidence.CONFIDENCE,
                UserFactEvidence.OBSERVED_AT,
            )
            .values(
                UUID.randomUUID(),
                factId,
                candidate.messageId,
                candidate.conversationMessageId,
                candidate.peerId,
                candidate.saidById,
                candidate.sourceKind.name,
                confidence,
                candidate.observedAt,
            )
            .onConflict(UserFactEvidence.FACT_ID, UserFactEvidence.PEER_ID, UserFactEvidence.CONVERSATION_MESSAGE_ID)
            .doNothing()
            .execute()
        return inserted == INSERTED_ROWS
    }

    private fun applyCardState(
        revive: Boolean,
        targetId: UUID,
        currentId: UUID,
        observedAt: OffsetDateTime,
        now: OffsetDateTime,
    ) {
        if (revive) {
            dsl.update(UserFacts.TABLE)
                .set(STATUS_FIELD, UserFactStatus.CANDIDATE.name)
                .setNull(UserFacts.SUPERSEDED_AT)
                .set(UserFacts.UPDATED_AT, now)
                .where(UserFacts.ID.eq(targetId))
                .execute()
        }
        if (currentId != targetId) {
            dsl.update(UserFacts.TABLE)
                .set(STATUS_FIELD, UserFactStatus.HISTORICAL.name)
                .set(UserFacts.SUPERSEDED_AT, observedAt)
                .set(UserFacts.UPDATED_AT, now)
                .where(UserFacts.ID.eq(currentId))
                .execute()
        }
    }

    private fun applyAggregatesAndTrim(
        factId: UUID,
        newConfidence: BigDecimal,
        observedAt: OffsetDateTime,
        threshold: Double,
        evidenceRefsLimit: Int,
        now: OffsetDateTime,
    ) {
        val thresholdValue = BigDecimal.valueOf(threshold)
        dsl.update(UserFacts.TABLE)
            .set(UserFacts.EVIDENCE_COUNT, DSL.field(EVIDENCE_COUNT_SQL, SQLDataType.INTEGER))
            .set(
                UserFacts.MAX_EVIDENCE_CONFIDENCE,
                DSL.field(MAX_EVIDENCE_CONFIDENCE_SQL, SQLDataType.NUMERIC, newConfidence),
            )
            .set(
                UserFacts.LAST_CONFIRMED_AT,
                DSL.field(LAST_CONFIRMED_SQL, SQLDataType.TIMESTAMPWITHTIMEZONE, observedAt),
            )
            .set(
                UserFacts.FIRST_OBSERVED_AT,
                DSL.field(FIRST_OBSERVED_SQL, SQLDataType.TIMESTAMPWITHTIMEZONE, observedAt),
            )
            .set(UserFacts.CONFIDENCE, confidenceField(newConfidence, thresholdValue))
            .set(STATUS_FIELD, statusField(newConfidence, thresholdValue))
            .set(UserFacts.CONFLICT, conflictField(newConfidence, thresholdValue))
            .set(UserFacts.UPDATED_AT, now)
            .where(UserFacts.ID.eq(factId))
            .execute()
        dsl.deleteFrom(UserFactEvidence.TABLE)
            .where(UserFactEvidence.FACT_ID.eq(factId))
            .and(
                UserFactEvidence.ID.notIn(
                    DSL.select(UserFactEvidence.ID)
                        .from(UserFactEvidence.TABLE)
                        .where(UserFactEvidence.FACT_ID.eq(factId))
                        .orderBy(
                            UserFactEvidence.OBSERVED_AT.desc(),
                            UserFactEvidence.CONVERSATION_MESSAGE_ID.desc(),
                        )
                        .limit(evidenceRefsLimit),
                ),
            )
            .execute()
    }

    private fun applyConflict(ids: List<UUID>, now: OffsetDateTime) {
        if (ids.isEmpty()) {
            return
        }
        dsl.update(UserFacts.TABLE)
            .set(UserFacts.CONFLICT, true)
            .set(STATUS_FIELD, UserFactStatus.CANDIDATE.name)
            .set(UserFacts.CONFIDENCE, DSL.field(CONFLICT_CONFIDENCE_SQL, SQLDataType.NUMERIC))
            .set(UserFacts.UPDATED_AT, now)
            .where(UserFacts.ID.`in`(ids))
            .execute()
    }
}

private const val BIRTHDAY_FACT_KEY = "birthday"
private const val BIRTHDAY_MONTH_DAY_VALUE = "value_payload ->> 'monthDay'"
private const val INITIAL_EVIDENCE_COUNT = 0
private const val MIN_PENDING_COUNT = 1
private const val INSERTED_ROWS = 1

private const val EVIDENCE_COUNT_SQL = "evidence_count + 1"
private const val MAX_EVIDENCE_CONFIDENCE_SQL = "greatest(max_evidence_confidence, ?::numeric)"
private const val LAST_CONFIRMED_SQL = "greatest(last_confirmed_at, ?::timestamptz)"
private const val FIRST_OBSERVED_SQL = "least(first_observed_at, ?::timestamptz)"
private const val CURSOR_GREATEST_ID_SQL =
    "greatest(knowledge_extraction_cursors.last_conversation_message_id, excluded.last_conversation_message_id)"
private const val CURSOR_GREATEST_COMPLETED_SQL =
    "greatest(knowledge_extraction_cursors.last_completed_at, excluded.last_completed_at)"

private val AGGREGATE_SQL =
    "round(least(${FactConfidenceCalculator.MAX_CONFIDENCE}, " +
        "greatest(max_evidence_confidence, ?::numeric) + ${FactConfidenceCalculator.EVIDENCE_BONUS} * " +
        "(least(evidence_count + 1, ${FactConfidenceCalculator.EVIDENCE_CAP}) - 1)), 3)"

private val CONFLICT_CONFIDENCE_SQL =
    "round(greatest(0.0, least(${FactConfidenceCalculator.MAX_CONFIDENCE}, max_evidence_confidence + " +
        "${FactConfidenceCalculator.EVIDENCE_BONUS} * " +
        "(least(evidence_count, ${FactConfidenceCalculator.EVIDENCE_CAP}) - 1)) - " +
        "${KnowledgeTemporalPolicy.CONFLICT_PENALTY}), 3)"

private val STATUS_FIELD: Field<String> = DSL.field("status", SQLDataType.VARCHAR)

private val BIRTHDAY_MONTH_DAY_FIELD: Field<String> = DSL.field(BIRTHDAY_MONTH_DAY_VALUE, SQLDataType.VARCHAR)

private fun birthdayMonthDay(monthDay: MonthDay): String {
    val format = "%02d-%02d"
    return format.format(monthDay.monthValue, monthDay.dayOfMonth)
}

private data class CursorRow(
    val lastConversationMessageId: Long,
    val lastCompletedAt: OffsetDateTime,
)

private data class CardState(
    val latestCurrent: UserFact?,
    val historicalSameValue: UserFact?,
)

private data class PreparedFact(
    val normalized: NormalizedFact,
    val valueHash: String,
    val newConfidence: BigDecimal,
    val transition: FactTransition,
    val initialStatus: UserFactStatus,
    val initialSupersededAt: OffsetDateTime?,
    val mergePayload: Boolean,
    val payload: Map<String, Any?>,
)

private fun prepareFact(
    candidate: UserFactCandidate,
    normalized: NormalizedFact,
    cards: CardState,
    threshold: Double,
): PreparedFact {
    val latestCurrent = cards.latestCurrent
    val transition = if (latestCurrent == null) {
        FactTransition.CREATE
    } else {
        KnowledgeTemporalPolicy.classify(
            candidate.category,
            latestCurrent,
            cards.historicalSameValue,
            KnowledgeValueHash.of(normalized.value),
            candidate.observedAt,
        )
    }
    val mergePayload = transition == FactTransition.SAME &&
        candidate.category == UserFactCategory.PROFILE_ATTRIBUTE &&
        normalized.factKey == BIRTHDAY_FACT_KEY
    val payload = if (mergePayload && latestCurrent != null) {
        KnowledgeTemporalPolicy.mergeBirthdayPayload(
            latestCurrent.valuePayload,
            latestCurrent.lastConfirmedAt,
            normalized.valuePayload,
            candidate.observedAt,
        )
    } else {
        normalized.valuePayload
    }
    val olderWithoutCard = transition == FactTransition.OLDER && cards.historicalSameValue == null
    val clamped = FactConfidenceCalculator.clamp(candidate.sourceKind, candidate.confidence)
    return PreparedFact(
        normalized = normalized,
        valueHash = KnowledgeValueHash.of(normalized.value),
        newConfidence = BigDecimal.valueOf(clamped)
            .setScale(FactConfidenceCalculator.SCALE, RoundingMode.HALF_UP),
        transition = transition,
        initialStatus = if (olderWithoutCard) {
            UserFactStatus.HISTORICAL
        } else {
            FactConfidenceCalculator.status(clamped, threshold)
        },
        initialSupersededAt = if (olderWithoutCard) latestCurrent?.lastConfirmedAt else null,
        mergePayload = mergePayload,
        payload = payload,
    )
}

private fun confidenceField(newConfidence: BigDecimal, threshold: BigDecimal): Field<BigDecimal> {
    val sql = "case when status = 'HISTORICAL' then $AGGREGATE_SQL " +
        "when conflict and $AGGREGATE_SQL < ?::numeric " +
        "then round(greatest(0.0, $AGGREGATE_SQL - ${KnowledgeTemporalPolicy.CONFLICT_PENALTY}), 3) " +
        "else $AGGREGATE_SQL end"
    return DSL.field(sql, SQLDataType.NUMERIC, newConfidence, newConfidence, threshold, newConfidence, newConfidence)
}

private fun statusField(newConfidence: BigDecimal, threshold: BigDecimal): Field<String> {
    val sql = "case when status = 'HISTORICAL' then 'HISTORICAL' " +
        "when conflict and $AGGREGATE_SQL >= ?::numeric then 'CONFIRMED' " +
        "when conflict then 'CANDIDATE' " +
        "when $AGGREGATE_SQL >= ?::numeric then 'CONFIRMED' " +
        "else 'CANDIDATE' end"
    return DSL.field(sql, SQLDataType.VARCHAR, newConfidence, threshold, newConfidence, threshold)
}

private fun conflictField(newConfidence: BigDecimal, threshold: BigDecimal): Field<Boolean> {
    val sql = "case when status = 'HISTORICAL' then conflict " +
        "when conflict and $AGGREGATE_SQL >= ?::numeric then false " +
        "when conflict then true " +
        "else false end"
    return DSL.field(sql, SQLDataType.BOOLEAN, newConfidence, threshold, newConfidence, threshold)
}

private fun toJsonb(objectMapper: ObjectMapper, payload: Map<String, Any?>): JSONB? {
    val json = if (payload.isEmpty()) null else objectMapper.writeValueAsString(payload)
    return json?.let { JSONB.valueOf(it) }
}

private fun Record.toStoredMessage(): StoredMessage = StoredMessage(
    id = get(Messages.ID, UUID::class.java),
    peerId = get(Messages.PEER_ID, Long::class.java),
    conversationMessageId = get(Messages.CONVERSATION_MESSAGE_ID, Long::class.java),
    fromId = get(Messages.FROM_ID, Long::class.java),
    messageText = get(Messages.MESSAGE_TEXT, String::class.java),
    date = get(Messages.DATE, Timestamp::class.java)?.toInstant()?.atOffset(ZoneOffset.UTC),
    forwardedMessages = get(Messages.FORWARDED_MESSAGES, String::class.java),
)

private fun Record.toUserFact(objectMapper: ObjectMapper): UserFact {
    val category = UserFactCategory.valueOf(get(UserFacts.CATEGORY, String::class.java))
    val lastConfirmedAt = get(UserFacts.LAST_CONFIRMED_AT, OffsetDateTime::class.java)
    return UserFact(
        id = get(UserFacts.ID, UUID::class.java),
        userId = get(UserFacts.USER_ID, Long::class.java),
        peerId = get(UserFacts.PEER_ID, Long::class.java),
        category = category,
        factKey = get(UserFacts.FACT_KEY, String::class.java),
        value = get(UserFacts.VALUE, String::class.java),
        valuePayload = get(UserFacts.VALUE_PAYLOAD, String::class.java)
            ?.let { json -> objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {}) }
            ?: emptyMap(),
        confidence = get(UserFacts.CONFIDENCE, BigDecimal::class.java).toDouble(),
        status = UserFactStatus.valueOf(get(UserFacts.STATUS, String::class.java)),
        evidenceCount = get(UserFacts.EVIDENCE_COUNT, Int::class.java),
        firstObservedAt = get(UserFacts.FIRST_OBSERVED_AT, OffsetDateTime::class.java),
        lastConfirmedAt = lastConfirmedAt,
        supersededAt = get(UserFacts.SUPERSEDED_AT, OffsetDateTime::class.java),
        conflict = get(UserFacts.CONFLICT, Boolean::class.java),
        stale = KnowledgeTemporalPolicy.isStale(category, lastConfirmedAt, OffsetDateTime.now(ZoneOffset.UTC)),
    )
}
