package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile

interface SaveUserProfilePort {
    fun save(profile: VkUserProfile)
}
