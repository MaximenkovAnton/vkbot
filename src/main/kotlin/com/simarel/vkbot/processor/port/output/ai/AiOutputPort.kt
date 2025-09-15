package com.simarel.vkbot.processor.port.output.ai

import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

fun interface AiOutputPort<REQ: AiOutputPortRequest, RESP: AiOutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface AiOutputPortRequest: OutputPortRequest

interface AiOutputPortResponse: OutputPortResponse