package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface PersistUserFactsPort : OutputPort<PersistUserFactsPort.Request, PersistUserFactsPort.Response> {
    data class Request(val candidate: UserFactCandidate) : OutputPortRequest

    data class Response(val saved: Boolean) : OutputPortResponse
}
