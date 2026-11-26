package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.Duration

interface AcquireLongPollLeasePort :
    OutputPort<
        AcquireLongPollLeasePort.AcquireLongPollLeaseRequest,
        AcquireLongPollLeasePort.AcquireLongPollLeaseResponse,
        > {
    data class AcquireLongPollLeaseRequest(val owner: String, val ttl: Duration) : OutputPortRequest

    data class AcquireLongPollLeaseResponse(val acquired: Boolean) : OutputPortResponse
}
