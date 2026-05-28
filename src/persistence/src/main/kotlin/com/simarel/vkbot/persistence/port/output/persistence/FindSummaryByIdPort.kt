package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface FindSummaryByIdPort : OutputPort<FindSummaryByIdPort.FindSummaryByIdRequest, FindSummaryByIdPort.FindSummaryByIdResponse> {
    data class FindSummaryByIdRequest(val id: UUID) : OutputPortRequest
    data class FindSummaryByIdResponse(val summary: SummaryEntity?) : OutputPortResponse
}
