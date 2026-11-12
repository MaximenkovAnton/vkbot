package com.simarel.vkbot.ai.port.output.knowledge

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface UserKnowledgeSummaryPort :
    OutputPort<
        UserKnowledgeSummaryPort.Request,
        UserKnowledgeSummaryPort.Response,
        > {
    data class Request(val facts: String) : OutputPortRequest

    data class Response(val text: String) : OutputPortResponse
}
