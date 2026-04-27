package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkGroupProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.OffsetDateTime

@ApplicationScoped
open class SaveGroupProfileAdapter(
    private val repository: VkGroupProfilePanacheRepository,
) : SaveGroupProfilePort {

    @Transactional
    override fun save(profile: VkGroupProfile) {
        val entity = VkGroupProfileEntity().apply {
            // Группы хранятся в БД с положительным ID
            id = profile.id.value
            name = profile.name
            screenName = profile.screenName
            lastUpdated = profile.lastUpdated
            createdAt = OffsetDateTime.now()
        }
        repository.persist(entity)
    }
}
