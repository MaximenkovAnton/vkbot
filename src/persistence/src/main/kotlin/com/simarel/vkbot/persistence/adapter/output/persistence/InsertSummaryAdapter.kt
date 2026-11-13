package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.SummaryTable
import com.simarel.vkbot.persistence.port.output.persistence.InsertSummaryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class InsertSummaryAdapter(
    private val table: SummaryTable,
) : InsertSummaryPort {
    override fun execute(request: InsertSummaryPort.InsertSummaryRequest): InsertSummaryPort.InsertSummaryResponse {
        table.insert(request.summary)
        return InsertSummaryPort.InsertSummaryResponse()
    }
}
