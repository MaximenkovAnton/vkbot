package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserProfilesByIdsPort : OutputPort<FindUserProfilesByIdsPort.FindUserProfilesByIdsRequest, FindUserProfilesByIdsPort.FindUserProfilesByIdsResponse> {
    data class FindUserProfilesByIdsRequest(val fromIds: Collection<FromId>) : OutputPortRequest
    data class FindUserProfilesByIdsResponse(val profiles: List<VkUserProfile>) : OutputPortResponse
}
