package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindChatSummaryMessagesPort : FindChatSummaryMessagesPort {
    var messages: List<StoredMessage> = emptyList()
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<FindChatSummaryMessagesPort.Request>()

    override fun execute(
        request: FindChatSummaryMessagesPort.Request,
    ): FindChatSummaryMessagesPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return FindChatSummaryMessagesPort.Response(messages = messages)
    }
}
