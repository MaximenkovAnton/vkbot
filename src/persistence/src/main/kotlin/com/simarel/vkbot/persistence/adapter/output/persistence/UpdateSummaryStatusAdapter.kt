package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class UpdateSummaryStatusAdapter(
    private val table: SummaryTable,
) : UpdateSummaryStatusPort {
    override fun execute(request: UpdateSummaryStatusPort.UpdateSummaryStatusRequest): UpdateSummaryStatusPort.UpdateSummaryStatusResponse {
        table.updateStatus(request.summary)
        return UpdateSummaryStatusPort.UpdateSummaryStatusResponse()
    }
}
