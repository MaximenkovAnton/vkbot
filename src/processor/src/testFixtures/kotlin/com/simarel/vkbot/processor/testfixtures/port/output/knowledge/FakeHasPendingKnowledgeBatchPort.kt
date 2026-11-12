package com.simarel.vkbot.processor.testfixtures.port.output.knowledge

import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import java.util.concurrent.ConcurrentLinkedQueue

class FakeHasPendingKnowledgeBatchPort : HasPendingKnowledgeBatchPort {
    var hasPending: Boolean = false
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<HasPendingKnowledgeBatchPort.Request>()

    override fun execute(request: HasPendingKnowledgeBatchPort.Request): HasPendingKnowledgeBatchPort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return HasPendingKnowledgeBatchPort.Response(hasPending = hasPending)
    }
}
