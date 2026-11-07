package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.LocalDate

interface ClaimDailyGreetingPort :
    OutputPort<
        ClaimDailyGreetingPort.Request,
        ClaimDailyGreetingPort.Response,
        > {
    data class Request(val date: LocalDate, val peerId: PeerId) : OutputPortRequest
    data class Response(val claimed: Boolean) : OutputPortResponse
}
