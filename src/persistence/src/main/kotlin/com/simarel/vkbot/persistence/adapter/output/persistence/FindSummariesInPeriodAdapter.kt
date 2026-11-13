package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindSummariesInPeriodPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindSummariesInPeriodAdapter(
    private val table: SummaryTable,
) : FindSummariesInPeriodPort {
    override fun execute(
        request: FindSummariesInPeriodPort.Request,
    ): FindSummariesInPeriodPort.Response = FindSummariesInPeriodPort.Response(
        table.findByPeriod(
            request.peerId.value,
            request.from,
            request.to,
        ),
    )
}
