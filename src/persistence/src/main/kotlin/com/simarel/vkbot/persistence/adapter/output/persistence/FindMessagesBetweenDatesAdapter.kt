package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqMessageRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenDatesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindMessagesBetweenDatesAdapter(
    private val repository: JooqMessageRepository,
) : FindMessagesBetweenDatesPort {
    override fun execute(
        request: FindMessagesBetweenDatesPort.Request,
    ): FindMessagesBetweenDatesPort.Response = FindMessagesBetweenDatesPort.Response(
        repository.findMessagesBetweenDates(
            request.peerId.value,
            request.from,
            request.to,
            request.limit,
        ),
    )
}
