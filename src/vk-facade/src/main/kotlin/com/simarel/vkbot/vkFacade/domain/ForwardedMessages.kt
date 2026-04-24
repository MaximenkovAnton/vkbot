package com.simarel.vkbot.vkFacade.domain

import com.simarel.vkbot.share.domain.model.Message

data class ForwardedMessages(
    val peer_id: Long,
    val conversation_message_ids: List<Long>,
    val is_reply: Boolean,
) {
    constructor(message: Message) : this(
        peer_id = message.peerId.value,
        conversation_message_ids = listOf(message.conversationMessageId.value),
        is_reply = true
    )
}
