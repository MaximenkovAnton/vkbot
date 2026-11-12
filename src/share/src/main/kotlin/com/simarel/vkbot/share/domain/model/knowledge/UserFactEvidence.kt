package com.simarel.vkbot.share.domain.model.knowledge

import java.time.OffsetDateTime
import java.util.UUID

data class UserFactEvidence(
    val id: UUID,
    val factId: UUID,
    val messageId: UUID,
    val conversationMessageId: Long,
    val peerId: Long,
    val saidById: Long,
    val sourceKind: KnowledgeSourceKind,
    val confidence: Double,
    val observedAt: OffsetDateTime,
)
