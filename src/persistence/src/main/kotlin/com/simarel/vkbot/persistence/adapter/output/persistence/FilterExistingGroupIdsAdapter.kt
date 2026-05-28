package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkGroupProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingGroupIdsPort
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FilterExistingGroupIdsAdapter(
    private val repository: JooqVkGroupProfileRepository,
) : FilterExistingGroupIdsPort {

    @Transactional
    override fun execute(request: FilterExistingGroupIdsPort.FilterExistingGroupIdsRequest): FilterExistingGroupIdsPort.FilterExistingGroupIdsResponse {
        if (request.fromIds.isEmpty()) {
            return FilterExistingGroupIdsPort.FilterExistingGroupIdsResponse(emptySet())
        }
        // FromId для групп < 0, но в БД хранятся > 0
        val idValues = request.fromIds.map { it.value }
        val existingIds = repository.filterExistingIds(idValues)
        return FilterExistingGroupIdsPort.FilterExistingGroupIdsResponse(existingIds.map { FromId.of(it) }.toSet())
    }
}
