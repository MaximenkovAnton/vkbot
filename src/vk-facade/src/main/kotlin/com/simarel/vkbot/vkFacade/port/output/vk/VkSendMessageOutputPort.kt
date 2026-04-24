package com.simarel.vkbot.vkFacade.port.output.vk

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages

interface VkSendMessageOutputPort : OutputPort<VkSendMessageOutputRequest, VkSendMessageOutputResponse>

class VkSendMessageOutputRequest(
    val peerId: PeerId,
    val messageText: MessageText,
    val forwardedMessage: ForwardedMessages? = null,
    val rand: Int = 0,
) : OutputPortRequest

class VkSendMessageOutputResponse : OutputPortResponse
