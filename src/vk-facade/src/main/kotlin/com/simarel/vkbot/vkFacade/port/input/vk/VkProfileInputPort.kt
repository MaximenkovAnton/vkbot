package com.simarel.vkbot.vkFacade.port.input.vk

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface VkProfileInputPort : InputPort<VkProfileInputPort.VkProfileRequest, VkProfileInputPort.VkProfileResponse> {
    data class VkProfileRequest(
        val userIds: List<Long>,
        val groupIds: List<Long>,
    ) : InputPortRequest

    data class VkProfileResponse(
        val users: List<VkUserProfile>,
        val groups: List<VkGroupProfile>,
    ) : InputPortResponse
}
