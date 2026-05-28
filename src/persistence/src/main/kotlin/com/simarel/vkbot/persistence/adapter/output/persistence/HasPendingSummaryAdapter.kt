package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.HasPendingSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class HasPendingSummaryAdapter(
    private val repository: JooqSummaryRepository,
) : HasPendingSummaryPort {
    override fun execute(request: HasPendingSummaryPort.HasPendingSummaryRequest): HasPendingSummaryPort.HasPendingSummaryResponse {
        return HasPendingSummaryPort.HasPendingSummaryResponse(repository.hasPendingSummary(request.peerId))
    }
}
