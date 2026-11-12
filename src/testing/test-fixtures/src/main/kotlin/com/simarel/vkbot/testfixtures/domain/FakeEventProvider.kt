package com.simarel.vkbot.testfixtures.domain

import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.domain.event.SummaryRequestedPayload
import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload
import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload

object FakeEventProvider {

    fun createSummaryRequestedPayload(
        peerId: Long = 2_000_000_001,
        conversationMessageId: Long = 5_001,
    ) = SummaryRequestedPayload(
        peerId = peerId,
        conversationMessageId = conversationMessageId,
    )

    fun createUserKnowledgeRequestedPayload(
        peerId: Long = 2_000_000_001,
        upToConversationMessageId: Long = 101,
    ) = UserKnowledgeRequestedPayload(
        peerId = peerId,
        upToConversationMessageId = upToConversationMessageId,
    )

    fun createUserKnowledgeSummaryRequestedPayload(
        userId: Long = 10,
    ) = UserKnowledgeSummaryRequestedPayload(userId = userId)

    fun createChatKnowledgeSummaryRequestedPayload(
        peerId: Long = 2_000_000_001,
    ) = ChatKnowledgeSummaryRequestedPayload(peerId = peerId)
}
