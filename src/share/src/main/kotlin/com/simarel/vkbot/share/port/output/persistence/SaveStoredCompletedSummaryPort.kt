package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface SaveStoredCompletedSummaryPort : OutputPort<SaveStoredCompletedSummaryPort.Request, SaveStoredCompletedSummaryPort.Response> {
    data class Request(val summaryId: UUID, val shortSummary: String, val fullSummary: String) : OutputPortRequest
    class Response : OutputPortResponse
}
