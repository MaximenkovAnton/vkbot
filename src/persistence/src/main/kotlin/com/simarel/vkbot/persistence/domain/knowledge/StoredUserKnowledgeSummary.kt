package com.simarel.vkbot.persistence.domain.knowledge

import java.time.OffsetDateTime

data class StoredUserKnowledgeSummary(
    val userId: Long,
    val summaryText: String,
    val lastFactAt: OffsetDateTime,
    val summarizedAt: OffsetDateTime,
)
