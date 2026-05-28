package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindStoredGroupProfilesByIdsPort : OutputPort<FindStoredGroupProfilesByIdsPort.Request, FindStoredGroupProfilesByIdsPort.Response> {
    data class Request(val ids: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkGroupProfile>) : OutputPortResponse
}
