package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqKnowledgeSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindChatSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindChatSummaryAdapter(
    private val repository: JooqKnowledgeSummaryRepository,
) : FindChatSummaryPort {

    override fun execute(
        request: FindChatSummaryPort.Request,
    ): FindChatSummaryPort.Response = FindChatSummaryPort.Response(
        knowledge = repository.findChatSummary(request.peerId),
    )
}
