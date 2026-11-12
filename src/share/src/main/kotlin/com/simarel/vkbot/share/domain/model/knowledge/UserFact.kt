package com.simarel.vkbot.share.domain.model.knowledge

import java.time.OffsetDateTime
import java.util.UUID

data class UserFact(
    val id: UUID,
    val userId: Long,
    val peerId: Long,
    val category: UserFactCategory,
    val factKey: String,
    val value: String,
    val valuePayload: Map<String, Any?>,
    val confidence: Double,
    val status: UserFactStatus,
    val evidenceCount: Int,
    val firstObservedAt: OffsetDateTime,
    val lastConfirmedAt: OffsetDateTime,
    val supersededAt: OffsetDateTime?,
    val conflict: Boolean,
    val stale: Boolean,
)
