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
    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> {
        if (fromIds.isEmpty()) {
            return emptySet()
        }
        val idValues = fromIds.map { it.value }
        val existingIds = repository.filterExistingIds(idValues)
        return existingIds.map { FromId.of(it) }.toSet()
    }
}
