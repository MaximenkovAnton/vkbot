package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.LongPollPositionTable
import com.simarel.vkbot.persistence.port.output.persistence.SaveLongPollTsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class SaveLongPollTsAdapter(
    private val table: LongPollPositionTable,
) : SaveLongPollTsPort {
    override fun execute(
        request: SaveLongPollTsPort.SaveLongPollTsRequest,
    ): SaveLongPollTsPort.SaveLongPollTsResponse {
        table.save(request.ts)
        return SaveLongPollTsPort.SaveLongPollTsResponse
    }
}
