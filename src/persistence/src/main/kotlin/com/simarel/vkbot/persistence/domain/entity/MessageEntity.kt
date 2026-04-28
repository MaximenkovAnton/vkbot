package com.simarel.vkbot.persistence.domain.entity

import java.time.OffsetDateTime
import java.util.UUID

// Simple data class for message entity - no JPA annotations needed for jOOQ
open class MessageEntity {
    open var id: UUID? = null
    open var date: OffsetDateTime? = null
    open var fromId: Long? = null
    open var groupId: Long? = null
    open var peerId: Long? = null
    open var conversationMessageId: Long? = null
    open var messageText: String? = null
    open var forwardedMessages: String? = null
}
