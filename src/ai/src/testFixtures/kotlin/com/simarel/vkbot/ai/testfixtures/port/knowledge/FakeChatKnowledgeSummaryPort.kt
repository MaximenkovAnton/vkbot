package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.ai.port.output.knowledge.ChatKnowledgeSummaryPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeChatKnowledgeSummaryPort : ChatKnowledgeSummaryPort {
    var text: String = "chat summary"
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<ChatKnowledgeSummaryPort.Request>()

    override fun execute(request: ChatKnowledgeSummaryPort.Request): ChatKnowledgeSummaryPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return ChatKnowledgeSummaryPort.Response(text = text)
    }
}
