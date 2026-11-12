package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserKnowledgeRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindPendingKnowledgeMessagesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindPendingKnowledgeMessagesAdapter(
    private val repository: JooqUserKnowledgeRepository,
) : FindPendingKnowledgeMessagesPort {

    override fun execute(
        request: FindPendingKnowledgeMessagesPort.Request,
    ): FindPendingKnowledgeMessagesPort.Response = FindPendingKnowledgeMessagesPort.Response(
        messages = repository.findPendingMessages(
            request.peerId,
            request.upToConversationMessageId,
            request.limit,
        ),
    )
}
