package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.LongPollLeaseTable
import com.simarel.vkbot.persistence.port.output.persistence.ReleaseLongPollLeasePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class ReleaseLongPollLeaseAdapter(
    private val table: LongPollLeaseTable,
) : ReleaseLongPollLeasePort {

    @Transactional
    override fun execute(
        request: ReleaseLongPollLeasePort.ReleaseLongPollLeaseRequest,
    ): ReleaseLongPollLeasePort.ReleaseLongPollLeaseResponse {
        table.release(request.owner)
        return ReleaseLongPollLeasePort.ReleaseLongPollLeaseResponse
    }
}
