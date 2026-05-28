package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindAiStoredGroupProfilesByIdsPort : OutputPort<FindAiStoredGroupProfilesByIdsPort.Request, FindAiStoredGroupProfilesByIdsPort.Response> {
    data class Request(val ids: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkGroupProfile>) : OutputPortResponse
}
