package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.JooqVkGroupProfileRepository
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class SaveGroupProfileAdapter(
    private val repository: JooqVkGroupProfileRepository,
) : SaveGroupProfilePort {

    @Transactional
    override fun save(profile: VkGroupProfile) {
        repository.persist(profile)
    }
}
