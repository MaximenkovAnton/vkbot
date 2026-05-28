package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindGroupProfilesByIdsPort : OutputPort<FindGroupProfilesByIdsPort.FindGroupProfilesByIdsRequest, FindGroupProfilesByIdsPort.FindGroupProfilesByIdsResponse> {
    data class FindGroupProfilesByIdsRequest(val fromIds: Collection<FromId>) : OutputPortRequest
    data class FindGroupProfilesByIdsResponse(val profiles: List<VkGroupProfile>) : OutputPortResponse
}
