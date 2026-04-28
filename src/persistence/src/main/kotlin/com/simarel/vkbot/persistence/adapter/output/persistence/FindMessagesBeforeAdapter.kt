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
    override fun findMessagesBefore(
        peerId: PeerId,
        beforeConversationMessageId: ConversationMessageId,
        limit: Int
    ): List<MessageEntity> {
        val results = repository.findMessagesBefore(
            peerId.value,
            beforeConversationMessageId.value,
            limit
        )
        return results.sortedBy { it.conversationMessageId!! }
    }
}
