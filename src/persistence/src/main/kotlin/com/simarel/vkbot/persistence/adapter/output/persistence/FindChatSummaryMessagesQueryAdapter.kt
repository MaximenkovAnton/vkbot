package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.KnowledgeSummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindChatSummaryMessagesQueryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindChatSummaryMessagesQueryAdapter(
    private val table: KnowledgeSummaryTable,
) : FindChatSummaryMessagesQueryPort {

    override fun execute(
        request: FindChatSummaryMessagesQueryPort.Request,
    ): FindChatSummaryMessagesQueryPort.Response = FindChatSummaryMessagesQueryPort.Response(
        messages = table.findChatSummaryWindow(request.peerId, request.limit),
    )
}
