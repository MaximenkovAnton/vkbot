package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveUserKnowledgeSummaryPort :
    OutputPort<
        SaveUserKnowledgeSummaryPort.Request,
        SaveUserKnowledgeSummaryPort.Response,
        > {
    data class Request(val userId: Long, val summaryText: String) : OutputPortRequest
    class Response : OutputPortResponse
}
