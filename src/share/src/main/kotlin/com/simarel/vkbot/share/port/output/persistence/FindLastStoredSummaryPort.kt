package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLastStoredSummaryPort : OutputPort<FindLastStoredSummaryPort.Request, FindLastStoredSummaryPort.Response> {
    data class Request(val peerId: Long) : OutputPortRequest
    data class Response(val summary: Summary?) : OutputPortResponse
}
