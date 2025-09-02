package com.simarel.port.input.vk

import com.simarel.port.input.InputPort
import com.simarel.port.input.InputRequest
import com.simarel.port.input.InputResponse

fun interface VkPort<REQ: VkRequest, RESP: VkResponse>: InputPort<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkRequest: InputRequest

interface VkResponse: InputResponse