package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindSummariesInPeriodPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindSummariesInPeriodAdapter(
    private val repository: JooqSummaryRepository,
) : FindSummariesInPeriodPort {
    override fun execute(
        request: FindSummariesInPeriodPort.Request,
    ): FindSummariesInPeriodPort.Response = FindSummariesInPeriodPort.Response(
        repository.findByPeriod(
            request.peerId.value,
            request.from,
            request.to,
        ),
    )
}
