package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId

interface FilterExistingGroupIdsPort {
    fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId>
}
