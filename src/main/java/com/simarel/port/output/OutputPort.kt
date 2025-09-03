package com.simarel.port.output

import com.simarel.port.Port
import com.simarel.port.PortRequest
import com.simarel.port.PortResponse

fun interface OutputPort<REQ: OutputPortRequest, RESP: OutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface OutputPortRequest: PortRequest

interface OutputPortResponse: PortResponse