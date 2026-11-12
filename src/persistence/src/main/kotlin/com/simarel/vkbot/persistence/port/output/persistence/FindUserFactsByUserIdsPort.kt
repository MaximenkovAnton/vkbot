package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.UserFact
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserFactsByUserIdsPort :
    OutputPort<
        FindUserFactsByUserIdsPort.Request,
        FindUserFactsByUserIdsPort.Response,
        > {
    data class Request(val userIds: List<Long>) : OutputPortRequest

    data class Response(val facts: List<UserFact>) : OutputPortResponse
}
