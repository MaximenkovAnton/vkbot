package com.simarel.port.output.ai

import com.simarel.port.Port
import com.simarel.port.Request
import com.simarel.port.Response
import com.simarel.port.output.OutputRequest
import com.simarel.port.output.OutputResponse

fun interface AiPort<REQ: AiOutputRequest, RESP: AiOutputResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface AiOutputRequest: OutputRequest

interface AiOutputResponse: OutputResponse