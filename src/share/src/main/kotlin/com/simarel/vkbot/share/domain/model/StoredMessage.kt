package com.simarel.vkbot.share.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class StoredMessage(
    val id: UUID?,
    val peerId: Long,
    val conversationMessageId: Long,
    val fromId: Long,
    val messageText: String?,
    val date: OffsetDateTime?,
    val forwardedMessages: String?,
)
