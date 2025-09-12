package com.simarel.share.port.input

import com.simarel.share.port.Port
import com.simarel.share.port.PortRequest
import com.simarel.share.port.PortResponse

fun interface InputPort<REQ: InputPortRequest, RESP: InputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface InputPortRequest: PortRequest

interface InputPortResponse: PortResponse