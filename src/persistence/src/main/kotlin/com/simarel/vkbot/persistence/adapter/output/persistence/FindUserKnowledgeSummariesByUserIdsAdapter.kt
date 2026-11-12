package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqKnowledgeSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserKnowledgeSummariesByUserIdsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserKnowledgeSummariesByUserIdsAdapter(
    private val repository: JooqKnowledgeSummaryRepository,
) : FindUserKnowledgeSummariesByUserIdsPort {

    override fun execute(
        request: FindUserKnowledgeSummariesByUserIdsPort.Request,
    ): FindUserKnowledgeSummariesByUserIdsPort.Response = FindUserKnowledgeSummariesByUserIdsPort.Response(
        summaries = repository.findUserSummaries(request.userIds),
    )
}
