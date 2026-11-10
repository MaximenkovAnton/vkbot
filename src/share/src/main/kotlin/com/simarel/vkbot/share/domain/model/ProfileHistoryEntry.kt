package com.simarel.vkbot.share.domain.model

import java.time.OffsetDateTime

data class ProfileHistoryEntry(
    val profileId: Long,
    val changedAt: OffsetDateTime,
    val changes: Map<String, Any?>,
)
