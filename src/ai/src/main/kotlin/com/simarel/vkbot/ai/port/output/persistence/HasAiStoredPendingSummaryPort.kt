package com.simarel.vkbot.ai.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface HasAiStoredPendingSummaryPort : OutputPort<HasAiStoredPendingSummaryPort.Request, HasAiStoredPendingSummaryPort.Response> {
    data class Request(val peerId: Long) : OutputPortRequest
    data class Response(val hasPending: Boolean) : OutputPortResponse
}
