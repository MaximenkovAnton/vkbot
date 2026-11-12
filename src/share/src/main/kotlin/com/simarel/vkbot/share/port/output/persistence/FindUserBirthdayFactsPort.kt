package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import java.time.MonthDay

interface FindUserBirthdayFactsPort :
    OutputPort<
        FindUserBirthdayFactsPort.Request,
        FindUserBirthdayFactsPort.Response,
        > {
    data class Request(val monthDay: MonthDay) : OutputPortRequest

    data class Response(val facts: List<UserFact>) : OutputPortResponse
}
