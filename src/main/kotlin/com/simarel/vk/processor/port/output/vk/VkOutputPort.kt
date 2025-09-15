package com.simarel.vk.processor.port.output.vk

import com.simarel.vk.share.port.output.OutputPort
import com.simarel.vk.share.port.output.OutputPortRequest
import com.simarel.vk.share.port.output.OutputPortResponse

interface VkOutputPort<REQ: VkOutputPortRequest, RESP: VkOutputPortResponse>: OutputPort<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkOutputPortRequest: OutputPortRequest

interface VkOutputPortResponse: OutputPortResponse