package com.simarel.processor.port.output.vk

import com.simarel.processor.domain.vo.MessageText
import com.simarel.processor.domain.vo.PeerId

interface VkSendMessageOutputPort: VkOutputPort<VkSendMessageOutputRequest, VkSendMessageOutputResponse>

class VkSendMessageOutputRequest(
    val peerId: PeerId,
    val messageText: MessageText
): VkOutputPortRequest

class VkSendMessageOutputResponse: VkOutputPortResponse