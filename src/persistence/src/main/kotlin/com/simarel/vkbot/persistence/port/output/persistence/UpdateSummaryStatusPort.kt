package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface UpdateSummaryStatusPort : OutputPort<UpdateSummaryStatusPort.UpdateSummaryStatusRequest, UpdateSummaryStatusPort.UpdateSummaryStatusResponse> {
    data class UpdateSummaryStatusRequest(val summary: SummaryEntity) : OutputPortRequest
    class UpdateSummaryStatusResponse : OutputPortResponse
}
