package com.simarel.port.input

import com.simarel.port.Port
import com.simarel.port.PortRequest
import com.simarel.port.PortResponse

fun interface InputPort<REQ: InputPortRequest, RESP: InputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface InputPortRequest: PortRequest

interface InputPortResponse: PortResponse