package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface FindStoredSummariesInPeriodPort :
    OutputPort<
        FindStoredSummariesInPeriodPort.Request,
        FindStoredSummariesInPeriodPort.Response,
        > {
    data class Request(
        val peerId: Long,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
    ) : OutputPortRequest

    data class Response(val summaries: List<Summary>) : OutputPortResponse
}
