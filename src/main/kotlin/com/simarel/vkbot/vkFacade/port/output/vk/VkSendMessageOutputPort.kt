package com.simarel.vkbot.vkFacade.port.output.vk

import com.simarel.vkbot.processor.domain.vo.MessageText
import com.simarel.vkbot.processor.domain.vo.PeerId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface VkSendMessageOutputPort: OutputPort<VkSendMessageOutputRequest, VkSendMessageOutputResponse>

class VkSendMessageOutputRequest(
    val peerId: PeerId,
    val messageText: MessageText
): OutputPortRequest

class VkSendMessageOutputResponse: OutputPortResponse