package com.simarel.vkbot.share.port.output

import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.PortRequest
import com.simarel.vkbot.share.port.PortResponse

fun interface OutputPort<REQ: OutputPortRequest, RESP: OutputPortResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface OutputPortRequest: PortRequest

interface OutputPortResponse: PortResponse