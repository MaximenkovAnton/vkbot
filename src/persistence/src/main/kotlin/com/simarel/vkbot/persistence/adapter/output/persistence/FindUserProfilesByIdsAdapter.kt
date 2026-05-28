package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FindUserProfilesByIdsAdapter(
    private val repository: JooqVkUserProfileRepository,
) : FindUserProfilesByIdsPort {

    @Transactional
    override fun execute(request: FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest): FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse(emptyList())
        }
        val idValues = request.fromIds.map { it.value }
        return FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse(repository.findByIds(idValues))
    }
}
