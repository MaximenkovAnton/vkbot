package com.simarel.port.input

import com.simarel.port.Port
import com.simarel.port.Request
import com.simarel.port.Response

fun interface InputPort<REQ: InputRequest, RESP: InputResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface InputRequest: Request

interface InputResponse: Response