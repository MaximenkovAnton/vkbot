package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveLongPollTsPort :
    OutputPort<
        SaveLongPollTsPort.SaveLongPollTsRequest,
        SaveLongPollTsPort.SaveLongPollTsResponse,
        > {
    data class SaveLongPollTsRequest(val ts: String) : OutputPortRequest
    data object SaveLongPollTsResponse : OutputPortResponse
}
