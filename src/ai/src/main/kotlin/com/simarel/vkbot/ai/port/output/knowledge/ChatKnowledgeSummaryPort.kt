package com.simarel.vkbot.ai.port.output.knowledge

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ChatKnowledgeSummaryPort :
    OutputPort<
        ChatKnowledgeSummaryPort.Request,
        ChatKnowledgeSummaryPort.Response,
        > {
    data class Request(val messages: List<String>) : OutputPortRequest

    data class Response(val text: String) : OutputPortResponse
}
