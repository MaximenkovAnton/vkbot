package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkUserProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingUserIdsPort
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class FilterExistingUserIdsAdapter(
    private val repository: VkUserProfilePanacheRepository,
) : FilterExistingUserIdsPort {

    @Transactional
    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> {
        if (fromIds.isEmpty()) {
            return emptySet()
        }
        val idValues = fromIds.map { it.value }
        val existingEntities = repository.find("id in (?1)", idValues)
            .list<VkUserProfileEntity>()
        return existingEntities.map { FromId.of(it.id!!) }.toSet()
    }
}
