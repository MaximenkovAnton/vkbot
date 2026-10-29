package com.simarel.vkbot.testfixtures.domain

import com.simarel.vkbot.share.domain.event.SummaryRequestedPayload

object FakeEventProvider {

    fun createSummaryRequestedPayload(
        peerId: Long = 2_000_000_001,
        conversationMessageId: Long = 5_001,
    ) = SummaryRequestedPayload(
        peerId = peerId,
        conversationMessageId = conversationMessageId,
    )
}
