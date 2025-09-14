package com.simarel.vk.share.port.input

import com.simarel.vk.share.port.Port
import com.simarel.vk.share.port.PortRequest
import com.simarel.vk.share.port.PortResponse

fun interface InputPort<REQ: InputPortRequest, RESP: InputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface InputPortRequest: PortRequest

interface InputPortResponse: PortResponse