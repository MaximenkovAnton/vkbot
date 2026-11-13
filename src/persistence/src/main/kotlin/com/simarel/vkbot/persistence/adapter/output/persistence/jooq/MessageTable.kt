package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.Messages
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
open class MessageTable {

    @Inject
    lateinit var dsl: DSLContext

    open fun persist(entity: MessageEntity) {
        dsl.insertInto(Messages.TABLE)
            .columns(
                Messages.ID,
                Messages.DATE,
                Messages.FROM_ID,
                Messages.GROUP_ID,
                Messages.PEER_ID,
                Messages.CONVERSATION_MESSAGE_ID,
                Messages.MESSAGE_TEXT,
                Messages.FORWARDED_MESSAGES,
            )
            .values(
                entity.id,
                entity.date?.let { Timestamp.from(it.toInstant()) },
                entity.fromId,
                entity.groupId,
                entity.peerId,
                entity.conversationMessageId,
                entity.messageText,
                entity.forwardedMessages?.let { JSONB.valueOf(it) }, // ?: DSL.inline(null, SQLDataType.JSONB)
            )
            .execute()
    }

    open fun findMessagesBefore(peerIdValue: Long, beforeConversationMessageId: Long, limit: Int): List<MessageEntity> {
        val records = dsl.select()
            .from(Messages.TABLE)
            .where(Messages.PEER_ID.eq(peerIdValue))
            .and(Messages.CONVERSATION_MESSAGE_ID.lessThan(beforeConversationMessageId))
            .orderBy(Messages.CONVERSATION_MESSAGE_ID.desc())
            .limit(limit)
            .fetch()
            .reversed()

        return records.map { it.toMessageEntity() }
    }

    open fun findMessagesBetweenDates(
        peerIdValue: Long,
        from: OffsetDateTime,
        to: OffsetDateTime,
        limit: Int,
    ): List<MessageEntity> {
        if (limit <= 0) {
            return emptyList()
        }
        val records = dsl.select()
            .from(Messages.TABLE)
            .where(Messages.PEER_ID.eq(peerIdValue))
            .and(
                Messages.DATE.between(
                    Timestamp.from(from.toInstant()),
                    Timestamp.from(to.toInstant()),
                ),
            )
            .orderBy(Messages.CONVERSATION_MESSAGE_ID.desc())
            .limit(limit)
            .fetch()
            .reversed()

        return records.map { it.toMessageEntity() }
    }

    open fun findLastByAuthor(
        peerIdValue: Long,
        fromIdValue: Long,
        beforeConversationMessageIdValue: Long,
    ): MessageEntity? {
        val record = dsl.select()
            .from(Messages.TABLE)
            .where(Messages.PEER_ID.eq(peerIdValue))
            .and(Messages.FROM_ID.eq(fromIdValue))
            .and(Messages.CONVERSATION_MESSAGE_ID.lessThan(beforeConversationMessageIdValue))
            .orderBy(Messages.CONVERSATION_MESSAGE_ID.desc())
            .limit(1)
            .fetchOne()
            ?: return null

        return record.toMessageEntity()
    }

    private fun Record.toMessageEntity(): MessageEntity = MessageEntity().apply {
        id = get(Messages.ID, UUID::class.java)
        date = get(Messages.DATE, OffsetDateTime::class.java)
        fromId = get(Messages.FROM_ID, Long::class.java)
        groupId = get(Messages.GROUP_ID, Long::class.java)
        peerId = get(Messages.PEER_ID, Long::class.java)
        conversationMessageId = get(Messages.CONVERSATION_MESSAGE_ID, Long::class.java)
        messageText = get(Messages.MESSAGE_TEXT, String::class.java)
        forwardedMessages = get(Messages.FORWARDED_MESSAGES, String::class.java)
    }

    open fun findPeerIdsByFromId(fromId: Long): Set<Long> = dsl.selectDistinct(Messages.PEER_ID)
        .from(Messages.TABLE)
        .where(Messages.FROM_ID.eq(fromId))
        .fetch()
        .map { it.get(Messages.PEER_ID, Long::class.java) }
        .toSet()

    open fun findGroupPeerIds(): Set<Long> = dsl.selectDistinct(Messages.PEER_ID)
        .from(Messages.TABLE)
        .where(Messages.PEER_ID.gt(GROUP_CHAT_PEER_ID_START))
        .fetch()
        .map { it.get(Messages.PEER_ID, Long::class.java) }
        .toSet()

    open fun findAuthorsByPeerId(peerIdValue: Long): List<ChatAuthor> {
        val authorCount = DSL.count()
        return dsl.select(Messages.FROM_ID, authorCount)
            .from(Messages.TABLE)
            .where(Messages.PEER_ID.eq(peerIdValue))
            .groupBy(Messages.FROM_ID)
            .orderBy(Messages.FROM_ID.asc())
            .fetch()
            .map { record ->
                ChatAuthor(
                    fromId = FromId.of(record.get(Messages.FROM_ID, Long::class.java)),
                    messageCount = record.get(authorCount, Int::class.java).toLong(),
                )
            }
    }

    companion object {
        private const val GROUP_CHAT_PEER_ID_START = 2_000_000_000L
    }
}
