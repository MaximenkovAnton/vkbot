package com.simarel.vkbot.share.domain.event

data class UserKnowledgeRequestedPayload(
    val peerId: Long,
    val upToConversationMessageId: Long,
)
