package com.simarel.vkbot.vkFacade.port.input.vk

import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages

interface VkSendMessageInputPort : InputPort<VkSendMessageInputRequest, VkSendMessageInputResponse>

class VkSendMessageInputRequest(
    val responseMessage: ResponseMessage,
    val forwardedMessages: ForwardedMessages? = null,
) : InputPortRequest

class VkSendMessageInputResponse : InputPortResponse
