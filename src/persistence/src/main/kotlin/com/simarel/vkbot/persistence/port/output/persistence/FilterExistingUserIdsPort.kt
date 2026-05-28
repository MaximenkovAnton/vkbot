package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FilterExistingUserIdsPort : OutputPort<FilterExistingUserIdsPort.FilterExistingUserIdsRequest, FilterExistingUserIdsPort.FilterExistingUserIdsResponse> {
    data class FilterExistingUserIdsRequest(val fromIds: Collection<FromId>) : OutputPortRequest
    data class FilterExistingUserIdsResponse(val existingIds: Set<FromId>) : OutputPortResponse
}
