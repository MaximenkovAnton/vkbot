package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBeforePort
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.PeerId
import io.quarkus.hibernate.orm.panache.PanacheQuery
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindMessagesBeforeAdapter(
    private val repository: MessagePanacheRepository,
) : FindMessagesBeforePort {

    override fun findMessagesBefore(
        peerId: PeerId,
        beforeConversationMessageId: ConversationMessageId,
        limit: Int
    ): List<MessageEntity> {
        val query: PanacheQuery<MessageEntity> = repository.find(
            "peerId = ?1 and conversationMessageId < ?2 order by conversationMessageId desc",
            peerId.value,
            beforeConversationMessageId.value
        )
        val results: List<MessageEntity> = query.list()
        return results.asSequence()
            .take(limit)
            .sortedBy { it.conversationMessageId!! }
            .toList()
    }
}
