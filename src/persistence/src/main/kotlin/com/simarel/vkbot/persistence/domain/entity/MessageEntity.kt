package com.simarel.vkbot.persistence.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    open var messageText: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forwarded_messages")
    open var forwardedMessages: String? = null
}
