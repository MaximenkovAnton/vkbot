package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqLongPollPositionRepository
import com.simarel.vkbot.persistence.port.output.persistence.SaveLongPollTsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class SaveLongPollTsAdapter(
    private val repository: JooqLongPollPositionRepository,
) : SaveLongPollTsPort {
    override fun execute(
        request: SaveLongPollTsPort.SaveLongPollTsRequest,
    ): SaveLongPollTsPort.SaveLongPollTsResponse {
        repository.save(request.ts)
        return SaveLongPollTsPort.SaveLongPollTsResponse
    }
}
