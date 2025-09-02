package com.simarel.port.output

import com.simarel.port.Port
import com.simarel.port.Request
import com.simarel.port.Response

fun interface OutputPort<REQ: OutputRequest, RESP: OutputResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface OutputRequest: Request

interface OutputResponse: Response