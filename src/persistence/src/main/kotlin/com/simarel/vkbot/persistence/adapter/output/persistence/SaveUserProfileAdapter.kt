package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkUserProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.SaveUserProfilePort
import com.simarel.vkbot.share.domain.model.VkUserProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveUserProfileAdapter(
    private val repository: JooqVkUserProfileRepository,
) : SaveUserProfilePort {

    @Transactional
    override fun save(profile: VkUserProfile) {
        repository.persist(profile)
    }
}
