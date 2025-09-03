package com.simarel.port.input.vk

import com.simarel.port.input.InputPort
import com.simarel.port.input.InputPortRequest
import com.simarel.port.input.InputPortResponse

fun interface VkPort<REQ: VkPortRequest, RESP: VkPortResponse>: InputPort<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkPortRequest: InputPortRequest

interface VkPortResponse: InputPortResponse