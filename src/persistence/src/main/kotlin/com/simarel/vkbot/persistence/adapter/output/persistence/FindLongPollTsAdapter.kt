package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.LongPollPositionTable
import com.simarel.vkbot.persistence.port.output.persistence.FindLongPollTsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindLongPollTsAdapter(
    private val table: LongPollPositionTable,
) : FindLongPollTsPort {
    override fun execute(
        request: FindLongPollTsPort.FindLongPollTsRequest,
    ): FindLongPollTsPort.FindLongPollTsResponse = FindLongPollTsPort.FindLongPollTsResponse(table.find())
}
