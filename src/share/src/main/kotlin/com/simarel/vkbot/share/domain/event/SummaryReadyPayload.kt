package com.simarel.vkbot.share.domain.event

data class SummaryReadyPayload(
    val peerId: Long,
    val messageText: String,
    val firstConversationMessageId: Long,
    val lastConversationMessageId: Long,
)
