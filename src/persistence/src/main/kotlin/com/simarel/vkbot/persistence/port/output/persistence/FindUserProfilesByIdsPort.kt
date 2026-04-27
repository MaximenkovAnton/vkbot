package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface FindUserProfilesByIdsPort {
    fun findByIds(fromIds: Collection<FromId>): List<VkUserProfile>
}
