package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface SaveAiStoredCompletedSummaryPort : OutputPort<SaveAiStoredCompletedSummaryPort.Request, SaveAiStoredCompletedSummaryPort.Response> {
    data class Request(val summaryId: UUID, val shortSummary: String, val fullSummary: String) : OutputPortRequest
    class Response : OutputPortResponse
}
