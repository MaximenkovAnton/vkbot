package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface FindUserKnowledgeSummaryStatePort :
    OutputPort<
        FindUserKnowledgeSummaryStatePort.Request,
        FindUserKnowledgeSummaryStatePort.Response,
        > {
    data class Request(val userId: Long) : OutputPortRequest

    data class Response(
        val summarizedAt: OffsetDateTime?,
        val factsSinceSummary: Int,
    ) : OutputPortResponse
}
