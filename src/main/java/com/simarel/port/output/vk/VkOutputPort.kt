package com.simarel.port.output.vk

import com.simarel.port.output.OutputPort
import com.simarel.port.output.OutputPortRequest
import com.simarel.port.output.OutputPortResponse

interface VkOutputPort<REQ: VkOutputPortRequest, RESP: VkOutputPortResponse>: OutputPort<REQ, RESP> {
    override fun execute(request: REQ): RESP
}

interface VkOutputPortRequest: OutputPortRequest

interface VkOutputPortResponse: OutputPortResponse