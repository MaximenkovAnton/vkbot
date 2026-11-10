package com.simarel.vkbot.persistence.domain.profile

import java.time.OffsetDateTime

data class ProfileHistoryEntry(
    val profileId: Long,
    val changedAt: OffsetDateTime,
    val changes: Map<String, Any?>,
)
