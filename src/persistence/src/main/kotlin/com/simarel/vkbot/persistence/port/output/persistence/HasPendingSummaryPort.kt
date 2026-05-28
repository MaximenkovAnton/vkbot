package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface HasPendingSummaryPort : OutputPort<HasPendingSummaryPort.HasPendingSummaryRequest, HasPendingSummaryPort.HasPendingSummaryResponse> {
    data class HasPendingSummaryRequest(val peerId: Long) : OutputPortRequest
    data class HasPendingSummaryResponse(val hasPending: Boolean) : OutputPortResponse
}
