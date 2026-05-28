package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindStoredUserProfilesByIdsPort : OutputPort<FindStoredUserProfilesByIdsPort.Request, FindStoredUserProfilesByIdsPort.Response> {
    data class Request(val ids: List<FromId>) : OutputPortRequest
    data class Response(val profiles: List<VkUserProfile>) : OutputPortResponse
}
