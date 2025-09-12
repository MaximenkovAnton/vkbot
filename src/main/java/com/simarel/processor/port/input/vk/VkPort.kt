package com.simarel.processor.port.input.vk

import com.simarel.share.port.input.InputPort
import com.simarel.share.port.input.InputPortRequest
import com.simarel.share.port.input.InputPortResponse

fun interface VkPort<REQ: VkPortRequest, RESP: VkPortResponse>: InputPort<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkPortRequest: InputPortRequest

interface VkPortResponse: InputPortResponse