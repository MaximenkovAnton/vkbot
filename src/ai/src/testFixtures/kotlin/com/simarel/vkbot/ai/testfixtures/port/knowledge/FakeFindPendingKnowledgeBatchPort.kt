package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindPendingKnowledgeBatchPort : FindPendingKnowledgeBatchPort {
    var messages: List<StoredMessage> = emptyList()
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<FindPendingKnowledgeBatchPort.Request>()

    override fun execute(request: FindPendingKnowledgeBatchPort.Request): FindPendingKnowledgeBatchPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return FindPendingKnowledgeBatchPort.Response(messages = messages)
    }
}
