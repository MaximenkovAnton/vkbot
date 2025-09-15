package com.simarel.vkbot.receiver.port.input

import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.receiver.domain.vo.VkResponse
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ReceiveMessageInputPort: InputPort<VkConfirmationInputPortRequest, VkConfirmationInputPortResponse>

@JvmInline
value class VkConfirmationInputPortRequest(val vkEvent: VkEvent): InputPortRequest

@JvmInline
value class VkConfirmationInputPortResponse(val response: VkResponse): InputPortResponse