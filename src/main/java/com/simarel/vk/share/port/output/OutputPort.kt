package com.simarel.vk.share.port.output

import com.simarel.vk.share.port.Port
import com.simarel.vk.share.port.PortRequest
import com.simarel.vk.share.port.PortResponse

fun interface OutputPort<REQ: OutputPortRequest, RESP: OutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface OutputPortRequest: PortRequest

interface OutputPortResponse: PortResponse