package com.simarel.vkbot.ai.testfixtures.port.knowledge

import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgeSummaryStatePort
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class FakeFindUserKnowledgeSummaryStatePort : FindUserKnowledgeSummaryStatePort {
    var summarizedAt: OffsetDateTime? = null
    var factsSinceSummary: Int = 0
    var failure: RuntimeException? = null
    val executeCalls = ConcurrentLinkedQueue<FindUserKnowledgeSummaryStatePort.Request>()

    override fun execute(
        request: FindUserKnowledgeSummaryStatePort.Request,
    ): FindUserKnowledgeSummaryStatePort.Response {
        executeCalls.add(request)
        failure?.let { throw it }
        return FindUserKnowledgeSummaryStatePort.Response(
            summarizedAt = summarizedAt,
            factsSinceSummary = factsSinceSummary,
        )
    }
}
