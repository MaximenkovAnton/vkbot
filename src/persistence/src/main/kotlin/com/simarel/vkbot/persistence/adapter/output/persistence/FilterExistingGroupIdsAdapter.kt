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
    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> {
        if (fromIds.isEmpty()) {
            return emptySet()
        }
        // FromId для групп < 0, но в БД хранятся > 0
        val idValues = fromIds.map { it.value }
        val existingIds = repository.filterExistingIds(idValues)
        return existingIds.map { FromId.of(it) }.toSet()
    }
}
