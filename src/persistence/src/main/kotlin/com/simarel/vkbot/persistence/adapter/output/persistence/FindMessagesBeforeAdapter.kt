package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBeforePort
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.PeerId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FindMessagesBeforeAdapter(
    private val repository: JooqMessageRepository,
) : FindMessagesBeforePort {

    @Transactional
    override fun execute(request: FindMessagesBeforePort.FindMessagesBeforeRequest): FindMessagesBeforePort.FindMessagesBeforeResponse {
        val results = repository.findMessagesBefore(
            request.peerId.value,
            request.beforeConversationMessageId.value,
            request.limit
        )
        return FindMessagesBeforePort.FindMessagesBeforeResponse(results.sortedBy { it.conversationMessageId!! })
    }
}
