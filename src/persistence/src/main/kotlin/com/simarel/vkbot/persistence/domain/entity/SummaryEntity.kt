package com.simarel.vkbot.persistence.domain.entity

import java.time.OffsetDateTime
import java.util.UUID

open class SummaryEntity {
    open var id: UUID? = null
    open var peerId: Long? = null
    open var firstMessageId: Long? = null
    open var lastMessageId: Long? = null
    open var fullSummary: String? = null
    open var shortSummary: String? = null
    open var status: SummaryStatus? = null
    open var createdAt: OffsetDateTime? = null
    open var updatedAt: OffsetDateTime? = null
}

enum class SummaryStatus {
    PENDING,
    COMPLETED,
    FAILED
}
