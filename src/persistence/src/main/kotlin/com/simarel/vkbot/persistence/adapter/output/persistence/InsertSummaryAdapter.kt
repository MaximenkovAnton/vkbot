package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqSummaryRepository
import com.simarel.vkbot.persistence.port.output.persistence.InsertSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class InsertSummaryAdapter(
    private val repository: JooqSummaryRepository,
) : InsertSummaryPort {
    override fun execute(request: InsertSummaryPort.InsertSummaryRequest): InsertSummaryPort.InsertSummaryResponse {
        repository.insert(request.summary)
        return InsertSummaryPort.InsertSummaryResponse()
    }
}
