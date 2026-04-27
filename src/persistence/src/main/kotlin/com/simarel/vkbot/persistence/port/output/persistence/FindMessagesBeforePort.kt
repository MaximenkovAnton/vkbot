package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.PeerId

interface FindMessagesBeforePort {
    fun findMessagesBefore(
        peerId: PeerId,
        beforeConversationMessageId: ConversationMessageId,
        limit: Int
    ): List<MessageEntity>
}
