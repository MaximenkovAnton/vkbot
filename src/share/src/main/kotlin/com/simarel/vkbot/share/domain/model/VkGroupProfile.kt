package com.simarel.vkbot.share.domain.model

import com.simarel.vkbot.share.domain.vo.FromId
import java.time.OffsetDateTime

data class VkGroupProfile(
    val id: FromId,
    val name: String?,
    val screenName: String?,
    val lastUpdated: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun of(
            id: Long?,
            name: String?,
            screenName: String? = null,
        ): VkGroupProfile = VkGroupProfile(
            id = FromId.of(id),
            name = name,
            screenName = screenName,
        )
    }
}
