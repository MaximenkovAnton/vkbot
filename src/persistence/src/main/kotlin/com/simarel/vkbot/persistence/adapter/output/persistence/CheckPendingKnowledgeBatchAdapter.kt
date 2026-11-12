package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqUserKnowledgeRepository
import com.simarel.vkbot.persistence.port.output.persistence.CheckPendingKnowledgeBatchPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class CheckPendingKnowledgeBatchAdapter(
    private val repository: JooqUserKnowledgeRepository,
) : CheckPendingKnowledgeBatchPort {

    override fun execute(
        request: CheckPendingKnowledgeBatchPort.Request,
    ): CheckPendingKnowledgeBatchPort.Response {
        val hasPending = repository.hasPendingBatch(request.peerId, request.minCount, request.minInterval)
        return CheckPendingKnowledgeBatchPort.Response(hasPending = hasPending)
    }
}
