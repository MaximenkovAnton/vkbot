package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindChatKnowledgeSummaryPort :
    OutputPort<
        FindChatKnowledgeSummaryPort.Request,
        FindChatKnowledgeSummaryPort.Response,
        > {
    data class Request(val peerId: Long) : OutputPortRequest

    data class Response(val knowledge: ChatKnowledge?) : OutputPortResponse
}
