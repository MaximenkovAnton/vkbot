package com.simarel.vkbot.share.domain.model

import com.simarel.vkbot.share.domain.vo.FromId
import java.time.OffsetDateTime

data class VkUserProfile(
    val id: FromId,
    val firstName: String?,
    val lastName: String?,
    val screenName: String?,
    val birthDate: String?,
    val city: String?,
    val lastUpdated: OffsetDateTime = OffsetDateTime.now(),
    val details: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun of(
            id: Long?,
            firstName: String?,
            lastName: String?,
            screenName: String? = null,
            birthDate: String? = null,
            city: String? = null,
            details: Map<String, Any?> = emptyMap(),
        ): VkUserProfile = VkUserProfile(
            id = FromId.of(id),
            firstName = firstName,
            lastName = lastName,
            screenName = screenName,
            birthDate = birthDate,
            city = city,
            details = details,
        )
    }
}
