package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqLongPollPositionRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindLongPollTsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class FindLongPollTsAdapter(
    private val repository: JooqLongPollPositionRepository,
) : FindLongPollTsPort {
    override fun execute(
        request: FindLongPollTsPort.FindLongPollTsRequest,
    ): FindLongPollTsPort.FindLongPollTsResponse = FindLongPollTsPort.FindLongPollTsResponse(repository.find())
}
