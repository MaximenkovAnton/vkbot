package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface UserProfileRepositoryPort {
    fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId>
    fun save(profile: VkUserProfile)
}
