package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface MarkAiStoredSummaryFailedPort : OutputPort<MarkAiStoredSummaryFailedPort.Request, MarkAiStoredSummaryFailedPort.Response> {
    data class Request(val summaryId: UUID) : OutputPortRequest
    class Response : OutputPortResponse
}
