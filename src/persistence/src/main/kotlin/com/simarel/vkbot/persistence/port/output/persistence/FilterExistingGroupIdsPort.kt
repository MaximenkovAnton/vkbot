package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FilterExistingGroupIdsPort : OutputPort<FilterExistingGroupIdsPort.FilterExistingGroupIdsRequest, FilterExistingGroupIdsPort.FilterExistingGroupIdsResponse> {
    data class FilterExistingGroupIdsRequest(val fromIds: Collection<FromId>) : OutputPortRequest
    data class FilterExistingGroupIdsResponse(val existingIds: Set<FromId>) : OutputPortResponse
}
