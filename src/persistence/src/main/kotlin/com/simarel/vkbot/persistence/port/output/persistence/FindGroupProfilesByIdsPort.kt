package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface FindGroupProfilesByIdsPort {
    fun findByIds(fromIds: Collection<FromId>): List<VkGroupProfile>
}
