package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusAndSummariesPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class UpdateSummaryStatusAndSummariesAdapter(
    private val repository: JooqSummaryRepository,
) : UpdateSummaryStatusAndSummariesPort {
    override fun execute(request: UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesRequest): UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesResponse {
        repository.updateStatusAndSummaries(request.summary)
        return UpdateSummaryStatusAndSummariesPort.UpdateSummaryStatusAndSummariesResponse()
    }
}
