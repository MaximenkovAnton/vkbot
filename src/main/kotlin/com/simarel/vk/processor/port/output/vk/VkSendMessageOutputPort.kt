package com.simarel.vk.processor.port.output.vk

import com.simarel.vk.processor.domain.vo.MessageText
import com.simarel.vk.processor.domain.vo.PeerId

interface VkSendMessageOutputPort: VkOutputPort<VkSendMessageOutputRequest, VkSendMessageOutputResponse>

class VkSendMessageOutputRequest(
    val peerId: PeerId,
    val messageText: MessageText
): VkOutputPortRequest

class VkSendMessageOutputResponse: VkOutputPortResponse