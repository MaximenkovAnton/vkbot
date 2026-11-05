package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLongPollTsPort :
    OutputPort<
        FindLongPollTsPort.FindLongPollTsRequest,
        FindLongPollTsPort.FindLongPollTsResponse,
        > {
    data object FindLongPollTsRequest : OutputPortRequest
    data class FindLongPollTsResponse(val ts: String?) : OutputPortResponse
}
