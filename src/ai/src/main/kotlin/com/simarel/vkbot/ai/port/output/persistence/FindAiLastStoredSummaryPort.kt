package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindAiLastStoredSummaryPort : OutputPort<FindAiLastStoredSummaryPort.Request, FindAiLastStoredSummaryPort.Response> {
    data class Request(val peerId: Long) : OutputPortRequest
    data class Response(val summary: Summary?) : OutputPortResponse
}
