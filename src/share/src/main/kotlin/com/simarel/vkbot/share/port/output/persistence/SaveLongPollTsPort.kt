package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveLongPollTsPort : OutputPort<SaveLongPollTsPort.Request, SaveLongPollTsPort.Response> {
    data class Request(val ts: LongPollTs) : OutputPortRequest
    data object Response : OutputPortResponse
}
