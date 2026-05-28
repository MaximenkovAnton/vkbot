package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindAiStoredUserProfilesByIdsPort : OutputPort<FindAiStoredUserProfilesByIdsPort.Request, FindAiStoredUserProfilesByIdsPort.Response> {
    data class Request(val ids: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkUserProfile>) : OutputPortResponse
}
