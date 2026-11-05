package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.LongPollTs
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindLongPollTsPort : OutputPort<FindLongPollTsPort.Request, FindLongPollTsPort.Response> {
    data object Request : OutputPortRequest
    data class Response(val ts: LongPollTs?) : OutputPortResponse
}
