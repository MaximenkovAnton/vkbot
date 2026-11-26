package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.IncomingMessages
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.time.OffsetDateTime

@ApplicationScoped
open class IncomingMessageTable {

    @Inject
    lateinit var dsl: DSLContext

    open fun register(
        peerId: Long,
        conversationMessageId: Long,
        payload: String,
    ): Long = dsl.insertInto(IncomingMessages.TABLE)
        .columns(
            IncomingMessages.PEER_ID,
            IncomingMessages.CONVERSATION_MESSAGE_ID,
            IncomingMessages.PAYLOAD,
        )
        .values(peerId, conversationMessageId, JSONB.valueOf(payload))
        .onConflict(IncomingMessages.PEER_ID, IncomingMessages.CONVERSATION_MESSAGE_ID)
        .doUpdate()
        .set(IncomingMessages.PAYLOAD, JSONB.valueOf(payload))
        .set(IncomingMessages.RECEIVED_AT, DSL.currentOffsetDateTime())
        .returningResult(IncomingMessages.ID)
        .fetchSingle()
        .value1()

    open fun purge(until: OffsetDateTime): Int = dsl.deleteFrom(IncomingMessages.TABLE)
        .where(IncomingMessages.RECEIVED_AT.lt(until))
        .execute()
}
