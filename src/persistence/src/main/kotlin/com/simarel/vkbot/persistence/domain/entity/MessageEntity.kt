package com.simarel.vkbot.persistence.domain.entity

import com.simarel.vkbot.persistence.domain.converter.JsonbConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "messages")
open class MessageEntity {
    @Id
    open var id: UUID? = null

    @Column(name = "date", nullable = false)
    open var date: OffsetDateTime? = null

    @Column(name = "from_id", nullable = false)
    open var fromId: Long? = null

    @Column(name = "group_id", nullable = false)
    open var groupId: Long? = null

    @Column(name = "peer_id", nullable = false)
    open var peerId: Long? = null

    @Column(name = "conversation_message_id", nullable = false)
    open var conversationMessageId: Long? = null

    @Column(name = "message_text", nullable = false)
    open var messageText: String? = null

    @Column(name = "forwarded_messages", columnDefinition = "JSONB")
    @Convert(converter = JsonbConverter::class)
    open var forwardedMessages: String? = null
}
