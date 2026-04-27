package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkUserProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.UserProfileRepositoryPort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import io.quarkus.hibernate.orm.panache.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class UserProfileRepositoryAdapter : PanacheRepository<VkUserProfileEntity>, UserProfileRepositoryPort {

    @Transactional
    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> {
        if (fromIds.isEmpty()) {
            return emptySet()
        }
        val idValues = fromIds.map { it.value }
        val existingEntities = find("id in (?1)", idValues).list<VkUserProfileEntity>()
        return existingEntities.map { FromId.of(it.id!!) }.toSet()
    }

    @Transactional
    override fun save(profile: VkUserProfile) {
        val entity = VkUserProfileEntity().apply {
            id = profile.id.value
            firstName = profile.firstName
            lastName = profile.lastName
            screenName = profile.screenName
            birthDate = profile.birthDate
            city = profile.city
            lastUpdated = profile.lastUpdated
            createdAt = OffsetDateTime.now()
        }
        persist(entity)
    }
}
