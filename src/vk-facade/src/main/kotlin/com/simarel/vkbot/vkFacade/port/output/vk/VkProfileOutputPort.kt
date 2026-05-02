package com.simarel.vkbot.vkFacade.port.output.vk

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId

interface VkProfileOutputPort {
    fun getUserProfilesBatch(fromIds: List<FromId>): List<VkUserProfile>
    fun getGroupProfilesBatch(fromIds: List<FromId>): List<VkGroupProfile>
}
