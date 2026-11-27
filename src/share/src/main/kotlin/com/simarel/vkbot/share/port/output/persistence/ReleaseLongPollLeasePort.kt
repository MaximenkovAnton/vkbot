package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ReleaseLongPollLeasePort :
    OutputPort<
        ReleaseLongPollLeasePort.Request,
        ReleaseLongPollLeasePort.Response,
        > {
    data class Request(val owner: String) : OutputPortRequest

    data object Response : OutputPortResponse
}
