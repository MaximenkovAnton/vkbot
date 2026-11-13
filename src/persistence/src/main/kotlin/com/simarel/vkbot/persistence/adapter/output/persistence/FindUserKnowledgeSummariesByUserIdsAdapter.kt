package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.KnowledgeSummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserKnowledgeSummariesByUserIdsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserKnowledgeSummariesByUserIdsAdapter(
    private val table: KnowledgeSummaryTable,
) : FindUserKnowledgeSummariesByUserIdsPort {

    override fun execute(
        request: FindUserKnowledgeSummariesByUserIdsPort.Request,
    ): FindUserKnowledgeSummariesByUserIdsPort.Response = FindUserKnowledgeSummariesByUserIdsPort.Response(
        summaries = table.findUserSummaries(request.userIds),
    )
}
