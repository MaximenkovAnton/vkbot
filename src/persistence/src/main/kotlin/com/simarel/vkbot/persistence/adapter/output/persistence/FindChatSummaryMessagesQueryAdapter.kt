package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqKnowledgeSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindChatSummaryMessagesQueryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindChatSummaryMessagesQueryAdapter(
    private val repository: JooqKnowledgeSummaryRepository,
) : FindChatSummaryMessagesQueryPort {

    override fun execute(
        request: FindChatSummaryMessagesQueryPort.Request,
    ): FindChatSummaryMessagesQueryPort.Response = FindChatSummaryMessagesQueryPort.Response(
        messages = repository.findChatSummaryWindow(request.peerId, request.limit),
    )
}
