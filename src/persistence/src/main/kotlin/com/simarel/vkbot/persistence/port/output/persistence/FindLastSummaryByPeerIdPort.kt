package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.SummaryEntity
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLastSummaryByPeerIdPort : OutputPort<FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdRequest, FindLastSummaryByPeerIdPort.FindLastSummaryByPeerIdResponse> {
    data class FindLastSummaryByPeerIdRequest(val peerId: Long) : OutputPortRequest
    data class FindLastSummaryByPeerIdResponse(val summary: SummaryEntity?) : OutputPortResponse
}
