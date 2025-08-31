package com.simarel.port.input.vk.callback

import com.simarel.port.Port
import com.simarel.port.Request
import com.simarel.port.Response

fun interface VkPort<REQ: VkRequest, RESP: VkResponse>: Port<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkRequest: Request

interface VkResponse: Response