package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqKnowledgeSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserSummaryStatePort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserSummaryStateAdapter(
    private val repository: JooqKnowledgeSummaryRepository,
) : FindUserSummaryStatePort {

    override fun execute(
        request: FindUserSummaryStatePort.Request,
    ): FindUserSummaryStatePort.Response {
        val summary = repository.findUserSummaries(listOf(request.userId)).firstOrNull()
            ?: return FindUserSummaryStatePort.Response(summarizedAt = null, factsSinceSummary = 0)
        return FindUserSummaryStatePort.Response(
            summarizedAt = summary.summarizedAt,
            factsSinceSummary = repository.countCurrentFactsNewerThan(request.userId, summary.summarizedAt),
        )
    }
}
