package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface UpsertUserKnowledgeSummaryPort :
    OutputPort<
        UpsertUserKnowledgeSummaryPort.Request,
        UpsertUserKnowledgeSummaryPort.Response,
        > {
    data class Request(val userId: Long, val summaryText: String) : OutputPortRequest

    class Response : OutputPortResponse
}
