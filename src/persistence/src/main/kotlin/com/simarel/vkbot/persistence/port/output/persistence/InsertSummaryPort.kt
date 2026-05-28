package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface InsertSummaryPort : OutputPort<InsertSummaryPort.InsertSummaryRequest, InsertSummaryPort.InsertSummaryResponse> {
    data class InsertSummaryRequest(val summary: SummaryEntity) : OutputPortRequest
    class InsertSummaryResponse : OutputPortResponse
}
