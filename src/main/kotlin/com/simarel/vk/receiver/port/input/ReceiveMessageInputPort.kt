package com.simarel.vk.receiver.port.input

import com.simarel.vk.receiver.domain.vo.VkEvent
import com.simarel.vk.receiver.domain.vo.VkResponse
import com.simarel.vk.share.port.input.InputPort
import com.simarel.vk.share.port.input.InputPortRequest
import com.simarel.vk.share.port.input.InputPortResponse

interface ReceiveMessageInputPort: InputPort<VkConfirmationInputPortRequest, VkConfirmationInputPortResponse>

@JvmInline
value class VkConfirmationInputPortRequest(val vkEvent: VkEvent): InputPortRequest

@JvmInline
value class VkConfirmationInputPortResponse(val response: VkResponse): InputPortResponse