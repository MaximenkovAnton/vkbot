package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.FindLastSummaryByPeerIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindLastSummaryByPeerIdAdapter(
    private val table: SummaryTable,
) : FindLastSummaryByPeerIdPort {
    override fun execute(request: FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdRequest): FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdResponse = FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdResponse(table.findLastByPeerId(request.peerId))
}
