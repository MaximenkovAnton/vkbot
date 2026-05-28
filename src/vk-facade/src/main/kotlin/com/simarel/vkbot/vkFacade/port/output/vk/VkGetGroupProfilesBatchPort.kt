package com.simarel.vkbot.vkFacade.port.output.vk

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface VkGetGroupProfilesBatchPort : OutputPort<VkGetGroupProfilesBatchPort.Request, VkGetGroupProfilesBatchPort.Response> {
    data class Request(val fromIds: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkGroupProfile>) : OutputPortResponse
}
