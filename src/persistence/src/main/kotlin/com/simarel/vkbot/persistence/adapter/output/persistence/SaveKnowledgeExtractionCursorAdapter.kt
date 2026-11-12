package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserKnowledgeRepository
import com.simarel.vkbot.persistence.port.output.persistence.SaveKnowledgeExtractionCursorPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveKnowledgeExtractionCursorAdapter(
    private val repository: JooqUserKnowledgeRepository,
) : SaveKnowledgeExtractionCursorPort {

    @Transactional
    override fun execute(
        request: SaveKnowledgeExtractionCursorPort.Request,
    ): SaveKnowledgeExtractionCursorPort.Response {
        repository.advanceCursor(request.peerId, request.lastConversationMessageId, request.completedAt)
        return SaveKnowledgeExtractionCursorPort.Response()
    }
}
