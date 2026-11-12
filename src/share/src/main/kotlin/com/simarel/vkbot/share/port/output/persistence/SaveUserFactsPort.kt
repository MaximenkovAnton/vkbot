package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveUserFactsPort : OutputPort<SaveUserFactsPort.Request, SaveUserFactsPort.Response> {
    data class Request(val candidates: List<UserFactCandidate>) : OutputPortRequest
    data class Response(val saved: Int, val skipped: Int) : OutputPortResponse
}
