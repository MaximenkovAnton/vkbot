package com.simarel.vkbot.vkFacade.port.input.vk

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.input.InputPort
import com.simarel.vkbot.share.port.input.InputPortRequest
import com.simarel.vkbot.share.port.input.InputPortResponse

interface VkSendMessageInputPort : InputPort<VkSendMessageInputRequest, VkSendMessageInputResponse>

class VkSendMessageInputRequest(
    val peerId: PeerId,
    val messageText: MessageText,
) : InputPortRequest

class VkSendMessageInputResponse : InputPortResponse
