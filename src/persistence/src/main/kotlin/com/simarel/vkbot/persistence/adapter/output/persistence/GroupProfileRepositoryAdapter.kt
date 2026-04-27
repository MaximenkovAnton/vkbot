package com.simarel.vkbot.persistence.adapter.output.persistence

import com.simarel.vkbot.persistence.port.output.persistence.FilterExistingGroupIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.FindGroupProfilesByIdsPort
import com.simarel.vkbot.persistence.port.output.persistence.GroupProfileRepositoryPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveGroupProfilePort
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
open class GroupProfileRepositoryAdapter(
    private val savePort: SaveGroupProfilePort,
    private val findByIdsPort: FindGroupProfilesByIdsPort,
    private val filterExistingPort: FilterExistingGroupIdsPort,
) : GroupProfileRepositoryPort {

    @Transactional
    override fun save(profile: VkGroupProfile) = savePort.save(profile)

    override fun findByIds(fromIds: Collection<FromId>): List<VkGroupProfile> =
        findByIdsPort.findByIds(fromIds)

    override fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId> =
        filterExistingPort.filterExistingIds(fromIds)
}
