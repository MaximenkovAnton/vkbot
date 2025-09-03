package com.simarel.port.output.ai

import com.simarel.port.Port
import com.simarel.port.output.OutputPortRequest
import com.simarel.port.output.OutputPortResponse

fun interface AiPort<REQ: AiOutputPortRequest, RESP: AiOutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface AiOutputPortRequest: OutputPortRequest

interface AiOutputPortResponse: OutputPortResponse