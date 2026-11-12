package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindChatKnowledgeSummaryPort : FindChatKnowledgeSummaryPort {
    var knowledge: ChatKnowledge? = null
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<FindChatKnowledgeSummaryPort.Request>()

    override fun execute(
        request: FindChatKnowledgeSummaryPort.Request,
    ): FindChatKnowledgeSummaryPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return FindChatKnowledgeSummaryPort.Response(knowledge = knowledge)
    }
}
