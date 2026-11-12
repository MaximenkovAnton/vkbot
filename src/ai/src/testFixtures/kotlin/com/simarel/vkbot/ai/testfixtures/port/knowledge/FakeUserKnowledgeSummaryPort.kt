package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.ai.port.output.knowledge.UserKnowledgeSummaryPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeUserKnowledgeSummaryPort : UserKnowledgeSummaryPort {
    var text: String = "user knowledge summary"
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<UserKnowledgeSummaryPort.Request>()

    override fun execute(request: UserKnowledgeSummaryPort.Request): UserKnowledgeSummaryPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return UserKnowledgeSummaryPort.Response(text = text)
    }
}
