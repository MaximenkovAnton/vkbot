package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindLastSummaryByPeerIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindLastSummaryByPeerIdAdapter(
    private val repository: JooqSummaryRepository,
) : FindLastSummaryByPeerIdPort {
    override fun execute(request: FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdRequest): FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdResponse {
        return FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdResponse(repository.findLastByPeerId(request.peerId))
    }
}
