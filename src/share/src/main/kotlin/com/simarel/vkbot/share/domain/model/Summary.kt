package com.simarel.vkbot.share.domain.model

import java.time.OffsetDateTime
import java.util.UUID

data class Summary(
    val id: UUID,
    val peerId: Long,
    val firstMessageId: Long?,
    val lastMessageId: Long?,
    val shortSummary: String?,
    val fullSummary: String?,
    val status: SummaryStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

enum class SummaryStatus {
    PENDING,
    COMPLETED,
    FAILED,
}

data class CreateSummaryRequest(
    val peerId: Long,
    val firstMessageId: Long,
    val lastMessageId: Long,
)

data class UpdateSummaryRequest(
    val id: UUID,
    val shortSummary: String?,
    val fullSummary: String?,
    val status: SummaryStatus,
)
