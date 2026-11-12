package com.simarel.vkbot.share.domain.model.knowledge

import java.time.OffsetDateTime
import java.util.UUID

data class UserFactCandidate(
    val userId: Long,
    val peerId: Long,
    val category: UserFactCategory,
    val factKey: String,
    val value: String,
    val valuePayload: Map<String, Any?>,
    val sourceKind: KnowledgeSourceKind,
    val confidence: Double,
    val messageId: UUID,
    val conversationMessageId: Long,
    val saidById: Long,
    val observedAt: OffsetDateTime,
) {
    companion object {
        const val MAX_VALUE_LENGTH = 500
        const val FACT_KEY_PATTERN = "^[a-z0-9._-]{1,96}$"
        val FACT_KEY_REGEX = Regex(FACT_KEY_PATTERN)
    }
}
