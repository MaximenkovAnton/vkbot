package com.simarel.vkbot.vkFacade.port.input.vk

import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface VkSendMessageInputPort : InputPort<VkSendMessageInputRequest, VkSendMessageInputResponse>

class VkSendMessageInputRequest(
    val responseMessage: ResponseMessage,
) : InputPortRequest

class VkSendMessageInputResponse : InputPortResponse
