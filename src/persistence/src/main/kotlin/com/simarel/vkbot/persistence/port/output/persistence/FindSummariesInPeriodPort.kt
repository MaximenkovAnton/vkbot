package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.OffsetDateTime

interface FindSummariesInPeriodPort :
    OutputPort<
        FindSummariesInPeriodPort.Request,
        FindSummariesInPeriodPort.Response,
        > {
    data class Request(
        val peerId: PeerId,
        val from: OffsetDateTime,
        val to: OffsetDateTime,
    ) : OutputPortRequest

    data class Response(val summaries: List<SummaryEntity>) : OutputPortResponse
}
