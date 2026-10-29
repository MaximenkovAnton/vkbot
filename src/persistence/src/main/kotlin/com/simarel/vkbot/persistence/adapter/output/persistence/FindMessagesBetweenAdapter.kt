package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindMessagesBetweenPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindMessagesBetweenAdapter(
    private val repository: JooqSummaryRepository,
) : FindMessagesBetweenPort {

    override fun execute(request: FindMessagesBetweenPort.FindMessagesBetweenRequest): FindMessagesBetweenPort.FindMessagesBetweenResponse = FindMessagesBetweenPort.FindMessagesBetweenResponse(
        repository.findMessagesBetween(
            request.peerId.value,
            request.firstMessageId.value,
            request.lastMessageId.value,
            request.limit,
        ),
    )
}
