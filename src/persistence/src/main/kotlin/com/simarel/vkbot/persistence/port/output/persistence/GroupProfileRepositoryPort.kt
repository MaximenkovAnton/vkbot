package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface GroupProfileRepositoryPort {
    fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId>
    fun save(profile: VkGroupProfile)
}
