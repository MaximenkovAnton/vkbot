package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindMessagesBetweenAdapter(
    private val table: SummaryTable,
) : FindMessagesBetweenPort {

    override fun execute(request: FindMessagesBetweenPort.FindMessagesBetweenRequest): FindMessagesBetweenPort.FindMessagesBetweenResponse = FindMessagesBetweenPort.FindMessagesBetweenResponse(
        table.findMessagesBetween(
            request.peerId.value,
            request.firstMessageId.value,
            request.lastMessageId.value,
            request.limit,
        ),
    )
}
