package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserKnowledgeTable
import com.simarel.vkbot.persistence.port.output.persistence.SaveKnowledgeExtractionCursorPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveKnowledgeExtractionCursorAdapter(
    private val table: UserKnowledgeTable,
) : SaveKnowledgeExtractionCursorPort {

    @Transactional
    override fun execute(
        request: SaveKnowledgeExtractionCursorPort.Request,
    ): SaveKnowledgeExtractionCursorPort.Response {
        table.advanceCursor(request.peerId, request.lastConversationMessageId, request.completedAt)
        return SaveKnowledgeExtractionCursorPort.Response()
    }
}
