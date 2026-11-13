package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserKnowledgeTable
import com.simarel.vkbot.persistence.port.output.persistence.FindPendingKnowledgeMessagesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindPendingKnowledgeMessagesAdapter(
    private val table: UserKnowledgeTable,
) : FindPendingKnowledgeMessagesPort {

    override fun execute(
        request: FindPendingKnowledgeMessagesPort.Request,
    ): FindPendingKnowledgeMessagesPort.Response = FindPendingKnowledgeMessagesPort.Response(
        messages = table.findPendingMessages(
            request.peerId,
            request.upToConversationMessageId,
            request.limit,
        ),
    )
}
