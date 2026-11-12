@file:Suppress("VariableNaming")

package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.ChatKnowledgeSummaries
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.Messages
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserFacts
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserKnowledgeSummaries
import com.simarel.vkbot.persistence.domain.knowledge.StoredUserKnowledgeSummary
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@ApplicationScoped
open class JooqKnowledgeSummaryRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun upsertUserSummary(userId: Long, summaryText: String, now: OffsetDateTime) {
        dsl.insertInto(UserKnowledgeSummaries.TABLE)
            .set(UserKnowledgeSummaries.USER_ID, userId)
            .set(SUMMARY_TEXT_FIELD, summaryText)
            .set(UserKnowledgeSummaries.FACTS_COVERED, factsCoveredField(userId))
            .set(UserKnowledgeSummaries.LAST_FACT_AT, lastFactAtField(userId, now))
            .set(UserKnowledgeSummaries.SUMMARIZED_AT, now)
            .onConflict(UserKnowledgeSummaries.USER_ID)
            .doUpdate()
            .set(SUMMARY_TEXT_FIELD, DSL.field("excluded.summary_text", SQLDataType.VARCHAR))
            .set(
                UserKnowledgeSummaries.FACTS_COVERED,
                DSL.field("excluded.facts_covered", SQLDataType.INTEGER),
            )
            .set(
                UserKnowledgeSummaries.LAST_FACT_AT,
                DSL.field("excluded.last_fact_at", SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .set(
                UserKnowledgeSummaries.SUMMARIZED_AT,
                DSL.field("excluded.summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .execute()
    }

    open fun upsertChatSummary(
        peerId: Long,
        summaryText: String,
        firstConversationMessageId: Long,
        lastConversationMessageId: Long,
        messagesCovered: Int,
        now: OffsetDateTime,
    ) {
        dsl.insertInto(ChatKnowledgeSummaries.TABLE)
            .set(ChatKnowledgeSummaries.PEER_ID, peerId)
            .set(SUMMARY_TEXT_FIELD, summaryText)
            .set(ChatKnowledgeSummaries.MESSAGES_COVERED, messagesCovered)
            .set(ChatKnowledgeSummaries.FIRST_CONVERSATION_MESSAGE_ID, firstConversationMessageId)
            .set(ChatKnowledgeSummaries.LAST_CONVERSATION_MESSAGE_ID, lastConversationMessageId)
            .set(ChatKnowledgeSummaries.SUMMARIZED_AT, now)
            .onConflict(ChatKnowledgeSummaries.PEER_ID)
            .doUpdate()
            .set(SUMMARY_TEXT_FIELD, DSL.field("excluded.summary_text", SQLDataType.VARCHAR))
            .set(ChatKnowledgeSummaries.MESSAGES_COVERED, DSL.field("excluded.messages_covered", SQLDataType.INTEGER))
            .set(
                ChatKnowledgeSummaries.FIRST_CONVERSATION_MESSAGE_ID,
                DSL.field("excluded.first_conversation_message_id", SQLDataType.BIGINT),
            )
            .set(
                ChatKnowledgeSummaries.LAST_CONVERSATION_MESSAGE_ID,
                DSL.field("excluded.last_conversation_message_id", SQLDataType.BIGINT),
            )
            .set(
                ChatKnowledgeSummaries.SUMMARIZED_AT,
                DSL.field("excluded.summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .execute()
    }

    open fun findUserSummaries(userIds: List<Long>): List<StoredUserKnowledgeSummary> {
        if (userIds.isEmpty()) {
            return emptyList()
        }
        return dsl.select(
            UserKnowledgeSummaries.USER_ID,
            UserKnowledgeSummaries.SUMMARY_TEXT,
            UserKnowledgeSummaries.LAST_FACT_AT,
            UserKnowledgeSummaries.SUMMARIZED_AT,
        )
            .from(UserKnowledgeSummaries.TABLE)
            .where(UserKnowledgeSummaries.USER_ID.`in`(userIds))
            .fetch()
            .map { record ->
                StoredUserKnowledgeSummary(
                    userId = record.get(UserKnowledgeSummaries.USER_ID, Long::class.java),
                    summaryText = record.get(UserKnowledgeSummaries.SUMMARY_TEXT, String::class.java),
                    lastFactAt = record.get(UserKnowledgeSummaries.LAST_FACT_AT, OffsetDateTime::class.java),
                    summarizedAt = record.get(UserKnowledgeSummaries.SUMMARIZED_AT, OffsetDateTime::class.java),
                )
            }
    }

    open fun countCurrentFactsNewerThan(userId: Long, summarizedAt: OffsetDateTime): Int = dsl.fetchCount(
        UserFacts.TABLE,
        UserFacts.USER_ID.eq(userId)
            .and(UserFacts.STATUS.ne(UserFactStatus.HISTORICAL.name))
            .and(UserFacts.LAST_CONFIRMED_AT.gt(summarizedAt)),
    )

    open fun findChatSummary(peerId: Long): ChatKnowledge? = dsl.select(
        ChatKnowledgeSummaries.SUMMARY_TEXT,
        ChatKnowledgeSummaries.SUMMARIZED_AT,
    )
        .from(ChatKnowledgeSummaries.TABLE)
        .where(ChatKnowledgeSummaries.PEER_ID.eq(peerId))
        .fetchOne()
        ?.let { record ->
            ChatKnowledge(
                peerId = peerId,
                summary = record.get(ChatKnowledgeSummaries.SUMMARY_TEXT, String::class.java),
                summarizedAt = record.get(ChatKnowledgeSummaries.SUMMARIZED_AT, OffsetDateTime::class.java),
            )
        }

    open fun findChatSummaryWindow(peerId: Long, limit: Int): List<StoredMessage> {
        if (limit <= 0) {
            return emptyList()
        }
        val lastSummarized = dsl.select(ChatKnowledgeSummaries.LAST_CONVERSATION_MESSAGE_ID)
            .from(ChatKnowledgeSummaries.TABLE)
            .where(ChatKnowledgeSummaries.PEER_ID.eq(peerId))
            .asField<Long>()
        val conversationMessageId: Field<Long> = DSL.field("conversation_message_id", SQLDataType.BIGINT)
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
            .where(Messages.PEER_ID.eq(peerId))
            .and(conversationMessageId.gt(DSL.coalesce(lastSummarized, DSL.inline(0L))))
            .orderBy(Messages.CONVERSATION_MESSAGE_ID.asc())
            .limit(limit)
            .fetch()
            .map { record -> record.toStoredMessage() }
    }
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

private val SUMMARY_TEXT_FIELD: Field<String> = DSL.field("summary_text", SQLDataType.VARCHAR)

private const val FACTS_COVERED_SQL =
    "(select count(*) from user_facts f where f.user_id = ? and f.status <> 'HISTORICAL')"

private const val LAST_FACT_AT_SQL =
    "coalesce((select max(f.last_confirmed_at) from user_facts f " +
        "where f.user_id = ? and f.status <> 'HISTORICAL'), ?::timestamptz)"

private fun factsCoveredField(userId: Long): Field<Int> {
    val sql = FACTS_COVERED_SQL
    return DSL.field(sql, SQLDataType.INTEGER, userId)
}

private fun lastFactAtField(userId: Long, now: OffsetDateTime): Field<OffsetDateTime> {
    val sql = LAST_FACT_AT_SQL
    return DSL.field(sql, SQLDataType.TIMESTAMPWITHTIMEZONE, userId, now)
}
