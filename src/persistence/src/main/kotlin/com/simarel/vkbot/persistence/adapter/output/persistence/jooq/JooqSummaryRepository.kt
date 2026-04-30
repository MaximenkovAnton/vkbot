package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.persistence.domain.entity.SummaryStatus
import com.simarel.vkbot.persistence.domain.entity.Tables.Messages as MessagesTable
import com.simarel.vkbot.persistence.domain.entity.Tables.Summaries
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
open class JooqSummaryRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun save(entity: SummaryEntity) {
        dsl.insertInto(Summaries.TABLE)
            .columns(
                Summaries.ID,
                Summaries.PEER_ID,
                Summaries.FIRST_MESSAGE_ID,
                Summaries.LAST_MESSAGE_ID,
                Summaries.FULL_SUMMARY,
                Summaries.SHORT_SUMMARY,
                Summaries.STATUS,
                Summaries.CREATED_AT,
                Summaries.UPDATED_AT
            )
            .values(
                entity.id,
                entity.peerId,
                entity.firstMessageId,
                entity.lastMessageId,
                entity.fullSummary,
                entity.shortSummary,
                entity.status?.name,
                entity.createdAt?.let { Timestamp.from(it.toInstant()) },
                entity.updatedAt?.let { Timestamp.from(it.toInstant()) }
            )
            .onConflict(Summaries.ID)
            .doUpdate()
            .set(Summaries.STATUS, entity.status?.name)
            .set(Summaries.FULL_SUMMARY, entity.fullSummary)
            .set(Summaries.SHORT_SUMMARY, entity.shortSummary)
            .set(Summaries.UPDATED_AT, entity.updatedAt?.let { Timestamp.from(it.toInstant()) })
            .execute()
    }

    open fun findLastByPeerId(peerIdValue: Long): SummaryEntity? {
        val record = dsl.select()
            .from(Summaries.TABLE)
            .where(Summaries.PEER_ID.eq(peerIdValue))
            .orderBy(Summaries.CREATED_AT.desc())
            .limit(1)
            .fetchOne()
            ?: return null

        return SummaryEntity().apply {
            id = record.get(Summaries.ID, UUID::class.java)
            peerId = record.get(Summaries.PEER_ID, Long::class.java)
            firstMessageId = record.get(Summaries.FIRST_MESSAGE_ID, Long::class.java)
            lastMessageId = record.get(Summaries.LAST_MESSAGE_ID, Long::class.java)
            fullSummary = record.get(Summaries.FULL_SUMMARY, String::class.java)
            shortSummary = record.get(Summaries.SHORT_SUMMARY, String::class.java)
            status = record.get(Summaries.STATUS, String::class.java)?.let { SummaryStatus.valueOf(it) }
            createdAt = record.get(Summaries.CREATED_AT, OffsetDateTime::class.java)
            updatedAt = record.get(Summaries.UPDATED_AT, OffsetDateTime::class.java)
        }
    }

    open fun countMessagesBetween(peerIdValue: Long, fromMessageId: Long, toMessageId: Long): Int {
        return dsl.fetchCount(
            dsl.select()
                .from(MessagesTable.TABLE)
                .where(MessagesTable.PEER_ID.eq(peerIdValue))
                .and(MessagesTable.CONVERSATION_MESSAGE_ID.greaterThan(fromMessageId))
                .and(MessagesTable.CONVERSATION_MESSAGE_ID.le(toMessageId))
        )
    }

    open fun findMessagesBetween(peerIdValue: Long, fromMessageId: Long, toMessageId: Long, limit: Int): List<MessageEntity> {
        val records = dsl.select()
            .from(MessagesTable.TABLE)
            .where(MessagesTable.PEER_ID.eq(peerIdValue))
            .and(MessagesTable.CONVERSATION_MESSAGE_ID.greaterThan(fromMessageId))
            .and(MessagesTable.CONVERSATION_MESSAGE_ID.le(toMessageId))
            .orderBy(MessagesTable.CONVERSATION_MESSAGE_ID.asc())
            .limit(limit)
            .fetch()

        return records.map { record ->
            MessageEntity().apply {
                id = record.get(MessagesTable.ID, UUID::class.java)
                date = record.get(MessagesTable.DATE, OffsetDateTime::class.java)
                fromId = record.get(MessagesTable.FROM_ID, Long::class.java)
                groupId = record.get(MessagesTable.GROUP_ID, Long::class.java)
                peerId = record.get(MessagesTable.PEER_ID, Long::class.java)
                conversationMessageId = record.get(MessagesTable.CONVERSATION_MESSAGE_ID, Long::class.java)
                messageText = record.get(MessagesTable.MESSAGE_TEXT, String::class.java)
                forwardedMessages = record.get(MessagesTable.FORWARDED_MESSAGES, String::class.java)
            }
        }
    }

    open fun hasPendingSummary(peerIdValue: Long): Boolean {
        val count = dsl.fetchCount(
            dsl.select()
                .from(Summaries.TABLE)
                .where(Summaries.PEER_ID.eq(peerIdValue))
                .and(Summaries.STATUS.eq(SummaryStatus.PENDING.name))
        )
        return count > 0
    }
}
