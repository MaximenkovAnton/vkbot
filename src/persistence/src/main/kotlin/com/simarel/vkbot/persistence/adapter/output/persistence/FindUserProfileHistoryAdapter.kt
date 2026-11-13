package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfileHistoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class FindUserProfileHistoryAdapter(
    private val table: VkUserProfileTable,
) : FindUserProfileHistoryPort {

    override fun execute(
        request: FindUserProfileHistoryPort.FindUserProfileHistoryRequest,
    ): FindUserProfileHistoryPort.FindUserProfileHistoryResponse {
        val entries = table.findHistory(request.profileId, request.limit, request.offset)
        return FindUserProfileHistoryPort.FindUserProfileHistoryResponse(entries)
    }
}
