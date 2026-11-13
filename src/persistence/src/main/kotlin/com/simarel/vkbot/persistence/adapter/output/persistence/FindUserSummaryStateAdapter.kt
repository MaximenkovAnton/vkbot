package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.KnowledgeSummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserSummaryStatePort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserSummaryStateAdapter(
    private val table: KnowledgeSummaryTable,
) : FindUserSummaryStatePort {

    override fun execute(
        request: FindUserSummaryStatePort.Request,
    ): FindUserSummaryStatePort.Response {
        val summary = table.findUserSummaries(listOf(request.userId)).firstOrNull()
            ?: return FindUserSummaryStatePort.Response(summarizedAt = null, factsSinceSummary = 0)
        return FindUserSummaryStatePort.Response(
            summarizedAt = summary.summarizedAt,
            factsSinceSummary = table.countCurrentFactsNewerThan(request.userId, summary.summarizedAt),
        )
    }
}
