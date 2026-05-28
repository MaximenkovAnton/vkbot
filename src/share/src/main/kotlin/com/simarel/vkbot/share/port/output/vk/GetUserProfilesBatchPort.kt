package com.simarel.vkbot.share.port.output.vk

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface GetUserProfilesBatchPort : OutputPort<GetUserProfilesBatchPort.Request, GetUserProfilesBatchPort.Response> {
    data class Request(val fromIds: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkUserProfile>) : OutputPortResponse
}
