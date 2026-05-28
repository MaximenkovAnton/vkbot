package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.UpdateSummaryStatusPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class UpdateSummaryStatusAdapter(
    private val repository: JooqSummaryRepository,
) : UpdateSummaryStatusPort {
    override fun execute(request: UpdateSummaryStatusPort.UpdateSummaryStatusRequest): UpdateSummaryStatusPort.UpdateSummaryStatusResponse {
        repository.updateStatus(request.summary)
        return UpdateSummaryStatusPort.UpdateSummaryStatusResponse()
    }
}
