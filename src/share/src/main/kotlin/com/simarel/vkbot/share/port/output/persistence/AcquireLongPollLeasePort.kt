package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.Duration

interface AcquireLongPollLeasePort :
    OutputPort<
        AcquireLongPollLeasePort.Request,
        AcquireLongPollLeasePort.Response,
        > {
    data class Request(val owner: String, val ttl: Duration) : OutputPortRequest

    data class Response(val acquired: Boolean) : OutputPortResponse
}
