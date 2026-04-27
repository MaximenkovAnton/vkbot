package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkUserProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class SaveUserProfileAdapter(
    private val repository: VkUserProfilePanacheRepository,
) : SaveUserProfilePort {

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
        repository.persist(entity)
    }
}
