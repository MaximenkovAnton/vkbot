package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.VkUserProfileTable
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FindUserProfilesByIdsAdapter(
    private val table: VkUserProfileTable,
) : FindUserProfilesByIdsPort {

    @Transactional
    override fun execute(request: FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest): FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse(emptyList())
        }
        val idValues = request.fromIds.map { it.value }
        return FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse(table.findByIds(idValues))
    }
}
