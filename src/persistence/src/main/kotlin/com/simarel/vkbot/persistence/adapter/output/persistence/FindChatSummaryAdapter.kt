package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.KnowledgeSummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindChatSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindChatSummaryAdapter(
    private val table: KnowledgeSummaryTable,
) : FindChatSummaryPort {

    override fun execute(
        request: FindChatSummaryPort.Request,
    ): FindChatSummaryPort.Response = FindChatSummaryPort.Response(
        knowledge = table.findChatSummary(request.peerId),
    )
}
