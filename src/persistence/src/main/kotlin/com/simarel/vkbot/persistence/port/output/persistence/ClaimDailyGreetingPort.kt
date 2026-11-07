package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.LocalDate

interface ClaimDailyGreetingPort :
    OutputPort<
        ClaimDailyGreetingPort.ClaimDailyGreetingRequest,
        ClaimDailyGreetingPort.ClaimDailyGreetingResponse,
        > {
    data class ClaimDailyGreetingRequest(val date: LocalDate, val peerId: PeerId) : OutputPortRequest
    data class ClaimDailyGreetingResponse(val claimed: Boolean) : OutputPortResponse
}
