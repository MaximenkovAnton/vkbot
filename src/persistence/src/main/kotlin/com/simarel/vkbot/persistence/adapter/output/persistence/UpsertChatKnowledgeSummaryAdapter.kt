package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.KnowledgeSummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.UpsertChatKnowledgeSummaryPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class UpsertChatKnowledgeSummaryAdapter(
    private val table: KnowledgeSummaryTable,
) : UpsertChatKnowledgeSummaryPort {

    @Transactional
    override fun execute(
        request: UpsertChatKnowledgeSummaryPort.Request,
    ): UpsertChatKnowledgeSummaryPort.Response {
        table.upsertChatSummary(
            request.peerId,
            request.summaryText,
            request.firstConversationMessageId,
            request.lastConversationMessageId,
            request.messagesCovered,
            OffsetDateTime.now(ZoneOffset.UTC),
        )
        return UpsertChatKnowledgeSummaryPort.Response()
    }
}
