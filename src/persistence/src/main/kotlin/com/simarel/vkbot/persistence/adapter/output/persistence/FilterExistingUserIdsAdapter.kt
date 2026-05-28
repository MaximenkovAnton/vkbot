package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingUserIdsPort
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FilterExistingUserIdsAdapter(
    private val repository: JooqVkUserProfileRepository,
) : FilterExistingUserIdsPort {

    @Transactional
    override fun execute(request: FilterExistingUserIdsPort.FilterExistingUserIdsRequest): FilterExistingUserIdsPort.FilterExistingUserIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FilterExistingUserIdsPort.FilterExistingUserIdsResponse(emptySet())
        }
        val idValues = request.fromIds.map { it.value }
        val existingIds = repository.filterExistingIds(idValues)
        return FilterExistingUserIdsPort.FilterExistingUserIdsResponse(existingIds.map { FromId.of(it) }.toSet())
    }
}
