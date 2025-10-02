package com.simarel.vkbot.share.port.input

import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.PortRequest
import com.simarel.vkbot.share.port.PortResponse

fun interface InputPort<REQ : InputPortRequest, RESP : InputPortResponse> : Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface InputPortRequest : PortRequest

interface InputPortResponse : PortResponse
