package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindSummaryByIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindSummaryByIdAdapter(
    private val table: SummaryTable,
) : FindSummaryByIdPort {
    override fun execute(request: FindSummaryByIdPort.FindSummaryByIdRequest): FindSummaryByIdPort.FindSummaryByIdResponse = FindSummaryByIdPort.FindSummaryByIdResponse(table.findById(request.id))
}
