package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ReleaseLongPollLeasePort :
    OutputPort<
        ReleaseLongPollLeasePort.ReleaseLongPollLeaseRequest,
        ReleaseLongPollLeasePort.ReleaseLongPollLeaseResponse,
        > {
    data class ReleaseLongPollLeaseRequest(val owner: String) : OutputPortRequest

    data object ReleaseLongPollLeaseResponse : OutputPortResponse
}
