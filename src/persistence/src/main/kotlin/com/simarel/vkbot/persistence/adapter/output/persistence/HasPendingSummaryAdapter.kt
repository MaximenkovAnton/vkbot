package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.HasPendingSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class HasPendingSummaryAdapter(
    private val table: SummaryTable,
) : HasPendingSummaryPort {
    override fun execute(request: HasPendingSummaryPort.HasPendingSummaryRequest): HasPendingSummaryPort.HasPendingSummaryResponse = HasPendingSummaryPort.HasPendingSummaryResponse(table.hasPendingSummary(request.peerId))
}
