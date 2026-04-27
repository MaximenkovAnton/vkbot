package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkGroupProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.GroupProfileRepositoryPort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import io.quarkus.hibernate.orm.panache.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class GroupProfileRepositoryAdapter : PanacheRepository<VkGroupProfileEntity>, GroupProfileRepositoryPort {

    @Transactional
    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> {
        if (fromIds.isEmpty()) {
            return emptySet()
        }
        val idValues = fromIds.map { it.value }
        val existingEntities = find("id in (?1)", idValues).list<VkGroupProfileEntity>()
        return existingEntities.map { FromId.of(it.id!!) }.toSet()
    }

    @Transactional
    override fun save(profile: VkGroupProfile) {
        val entity = VkGroupProfileEntity().apply {
            id = profile.id.value
            name = profile.name
            screenName = profile.screenName
            lastUpdated = profile.lastUpdated
            createdAt = OffsetDateTime.now()
        }
        persist(entity)
    }
}
