package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindChatSummaryPort :
    OutputPort<
        FindChatSummaryPort.Request,
        FindChatSummaryPort.Response,
        > {
    data class Request(val peerId: Long) : OutputPortRequest

    data class Response(val knowledge: ChatKnowledge?) : OutputPortResponse
}
