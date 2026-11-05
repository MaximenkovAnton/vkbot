package com.simarel.vkbot.receiver.port.input

import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface ReceiveMessageInputPort : InputPort<VkUpdateInputPortRequest, VkUpdateInputPortResponse>

data class VkUpdateInputPortRequest(val type: VkUpdateType, val message: Message? = null) : InputPortRequest

data object VkUpdateInputPortResponse : InputPortResponse
