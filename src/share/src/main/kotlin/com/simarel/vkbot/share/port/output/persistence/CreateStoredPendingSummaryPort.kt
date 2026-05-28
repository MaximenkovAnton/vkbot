package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.util.UUID

interface CreateStoredPendingSummaryPort : OutputPort<CreateStoredPendingSummaryPort.Request, CreateStoredPendingSummaryPort.Response> {
    data class Request(val peerId: Long, val firstMessageId: Long, val lastMessageId: Long) : OutputPortRequest
    data class Response(val summaryId: UUID) : OutputPortResponse
}
