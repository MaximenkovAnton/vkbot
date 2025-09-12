package com.simarel.share.port.output

import com.simarel.share.port.Port
import com.simarel.share.port.PortRequest
import com.simarel.share.port.PortResponse

fun interface OutputPort<REQ: OutputPortRequest, RESP: OutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface OutputPortRequest: PortRequest

interface OutputPortResponse: PortResponse