package com.simarel.vkbot.share.domain.model.knowledge

import java.time.OffsetDateTime

data class ChatKnowledge(
    val peerId: Long,
    val summary: String,
    val summarizedAt: OffsetDateTime,
)
