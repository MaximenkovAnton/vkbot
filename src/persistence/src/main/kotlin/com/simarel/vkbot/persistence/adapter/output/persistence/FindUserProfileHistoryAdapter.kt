package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfileHistoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserProfileHistoryAdapter(
    private val repository: JooqVkUserProfileRepository,
) : FindUserProfileHistoryPort {

    override fun execute(
        request: FindUserProfileHistoryPort.FindUserProfileHistoryRequest,
    ): FindUserProfileHistoryPort.FindUserProfileHistoryResponse {
        val entries = repository.findHistory(request.profileId, request.limit, request.offset)
        return FindUserProfileHistoryPort.FindUserProfileHistoryResponse(entries)
    }
}
