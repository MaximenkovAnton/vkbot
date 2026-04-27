package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.domain.entity.VkGroupProfileEntity
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import java.time.OffsetDateTime

@ApplicationScoped
open class FindGroupProfilesByIdsAdapter(
    private val repository: VkGroupProfilePanacheRepository,
) : FindGroupProfilesByIdsPort {

    override fun findByIds(fromIds: Collection<FromId>): List<VkGroupProfile> {
        if (fromIds.isEmpty()) {
            return emptyList()
        }
        // FromId для групп < 0, но в БД хранятся > 0
        val idValues = fromIds.map { -it.value }
        return repository.find("id in (?1)", idValues)
            .list<VkGroupProfileEntity>()
            .map { it.toDomain() }
    }

    private fun VkGroupProfileEntity.toDomain(): VkGroupProfile {
        return VkGroupProfile(
            id = FromId.of(-this.id!!), // Конвертируем положительный ID в отрицательный
            name = this.name,
            screenName = this.screenName,
            lastUpdated = this.lastUpdated ?: OffsetDateTime.now(),
        )
    }
}
