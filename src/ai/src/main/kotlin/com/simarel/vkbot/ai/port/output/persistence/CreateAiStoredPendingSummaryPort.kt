package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface CreateAiStoredPendingSummaryPort : OutputPort<CreateAiStoredPendingSummaryPort.Request, CreateAiStoredPendingSummaryPort.Response> {
    data class Request(val peerId: Long, val firstMessageId: Long, val lastMessageId: Long) : OutputPortRequest
    data class Response(val summaryId: UUID) : OutputPortResponse
}
