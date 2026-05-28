package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindSummaryByIdPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindSummaryByIdAdapter(
    private val repository: JooqSummaryRepository,
) : FindSummaryByIdPort {
    override fun execute(request: FindSummaryByIdPort.FindSummaryByIdRequest): FindSummaryByIdPort.FindSummaryByIdResponse {
        return FindSummaryByIdPort.FindSummaryByIdResponse(repository.findById(request.id))
    }
}
