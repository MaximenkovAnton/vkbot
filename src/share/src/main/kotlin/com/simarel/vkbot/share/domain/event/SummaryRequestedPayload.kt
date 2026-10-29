package com.simarel.vkbot.share.domain.event

data class SummaryRequestedPayload(
    val peerId: Long,
    val conversationMessageId: Long,
)
