package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.LongPollLeaseTable
import com.simarel.vkbot.persistence.port.output.persistence.AcquireLongPollLeasePort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class AcquireLongPollLeaseAdapter(
    private val table: LongPollLeaseTable,
) : AcquireLongPollLeasePort {

    @Transactional
    override fun execute(
        request: AcquireLongPollLeasePort.AcquireLongPollLeaseRequest,
    ): AcquireLongPollLeasePort.AcquireLongPollLeaseResponse {
        val acquired = table.acquire(request.owner, request.ttl)
        return AcquireLongPollLeasePort.AcquireLongPollLeaseResponse(acquired)
    }
}
