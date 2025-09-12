package com.simarel.receiver.port.input

import com.simarel.receiver.domain.vo.VkEvent
import com.simarel.receiver.domain.vo.VkResponse
import com.simarel.share.port.input.InputPort
import com.simarel.share.port.input.InputPortRequest
import com.simarel.share.port.input.InputPortResponse

interface ReceiveMessageInputPort: InputPort<VkConfirmationInputPortRequest, VkConfirmationInputPortResponse>

@JvmInline
value class VkConfirmationInputPortRequest(val vkEvent: VkEvent): InputPortRequest

@JvmInline
value class VkConfirmationInputPortResponse(val response: VkResponse): InputPortResponse