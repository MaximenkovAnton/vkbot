package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkUserProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.FindUserProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class FindUserProfilesByIdsAdapter(
    private val repository: VkUserProfilePanacheRepository,
) : FindUserProfilesByIdsPort {

    @Transactional
    override fun findByIds(fromIds: Collection<FromId>): List<VkUserProfile> {
        if (fromIds.isEmpty()) {
            return emptyList()
        }
        val idValues: List<Long> = fromIds.map { it.value }
        return repository.find("id in (?1)", idValues)
            .list<VkUserProfileEntity>()
            .map { it.toDomain() }
    }

    private fun VkUserProfileEntity.toDomain(): VkUserProfile {
        return VkUserProfile(
            id = FromId.of(this.id),
            firstName = this.firstName,
            lastName = this.lastName,
            screenName = this.screenName,
            birthDate = this.birthDate,
            city = this.city,
            lastUpdated = this.lastUpdated ?: OffsetDateTime.now(),
        )
    }
}
