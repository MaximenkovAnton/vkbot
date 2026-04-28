package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.domain.entity.Tables.Messages
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.JSONB
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.UUID

@ApplicationScoped
open class JooqMessageRepository {

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
                Messages.FORWARDED_MESSAGES
            )
            .values(
                entity.id,
                entity.date?.let { Timestamp.from(it.toInstant()) },
                entity.fromId,
                entity.groupId,
                entity.peerId,
                entity.conversationMessageId,
                entity.messageText,
                entity.forwardedMessages?.let { JSONB.valueOf(it) } //?: DSL.inline(null, SQLDataType.JSONB)
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

        return records.map { record ->
            MessageEntity().apply {
                id = record.get(Messages.ID, UUID::class.java)
                date = record.get(Messages.DATE)?.let {
                    (it as Timestamp).toInstant().atOffset(ZoneOffset.UTC)
                }
                fromId = record.get(Messages.FROM_ID, Long::class.java)
                groupId = record.get(Messages.GROUP_ID, Long::class.java)
                peerId = record.get(Messages.PEER_ID, Long::class.java)
                conversationMessageId = record.get(Messages.CONVERSATION_MESSAGE_ID, Long::class.java)
                messageText = record.get(Messages.MESSAGE_TEXT, String::class.java)
                forwardedMessages = record.get(Messages.FORWARDED_MESSAGES, String::class.java)
            }
        }
    }
}
