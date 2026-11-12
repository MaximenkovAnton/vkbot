package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface AdvanceExtractionCursorPort :
    OutputPort<
        AdvanceExtractionCursorPort.Request,
        AdvanceExtractionCursorPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val lastConversationMessageId: Long,
        val completedAt: OffsetDateTime,
    ) : OutputPortRequest

    class Response : OutputPortResponse
}
