package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.MessageTable
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenDatesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindMessagesBetweenDatesAdapter(
    private val table: MessageTable,
) : FindMessagesBetweenDatesPort {
    override fun execute(
        request: FindMessagesBetweenDatesPort.Request,
    ): FindMessagesBetweenDatesPort.Response = FindMessagesBetweenDatesPort.Response(
        table.findMessagesBetweenDates(
            request.peerId.value,
            request.from,
            request.to,
            request.limit,
        ),
    )
}
