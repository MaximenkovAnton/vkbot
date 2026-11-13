package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusAndSummariesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class UpdateSummaryStatusAndSummariesAdapter(
    private val table: SummaryTable,
) : UpdateSummaryStatusAndSummariesPort {
    override fun execute(request: UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesRequest): UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesResponse {
        table.updateStatusAndSummaries(request.summary)
        return UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesResponse()
    }
}
