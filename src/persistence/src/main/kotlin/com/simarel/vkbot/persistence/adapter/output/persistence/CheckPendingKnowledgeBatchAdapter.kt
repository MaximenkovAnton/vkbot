package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.UserKnowledgeTable
import com.simarel.vkbot.persistence.port.output.persistence.CheckPendingKnowledgeBatchPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class CheckPendingKnowledgeBatchAdapter(
    private val table: UserKnowledgeTable,
) : CheckPendingKnowledgeBatchPort {

    override fun execute(
        request: CheckPendingKnowledgeBatchPort.Request,
    ): CheckPendingKnowledgeBatchPort.Response {
        val hasPending = table.hasPendingBatch(request.peerId, request.minCount, request.minInterval)
        return CheckPendingKnowledgeBatchPort.Response(hasPending = hasPending)
    }
}
