package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface HasStoredPendingSummaryPort : OutputPort<HasStoredPendingSummaryPort.Request, HasStoredPendingSummaryPort.Response> {
    data class Request(val peerId: Long) : OutputPortRequest
    data class Response(val hasPending: Boolean) : OutputPortResponse
}
