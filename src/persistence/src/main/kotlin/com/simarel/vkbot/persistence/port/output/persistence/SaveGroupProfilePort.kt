package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile

interface SaveGroupProfilePort {
    fun save(profile: VkGroupProfile)
}
