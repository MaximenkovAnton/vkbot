package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId

interface FilterExistingUserIdsPort {
    fun filterExistingIds(fromIds: Collection<FromId>): Set<FromId>
}
