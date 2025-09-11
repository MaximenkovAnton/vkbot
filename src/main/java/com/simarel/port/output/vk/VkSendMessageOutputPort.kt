package com.simarel.port.output.vk

import com.simarel.domain.vo.MessageText
import com.simarel.domain.vo.PeerId

interface VkSendMessageOutputPort: VkOutputPort<VkSendMessageOutputRequest, VkSendMessageOutputResponse>

class VkSendMessageOutputRequest(
    val peerId: PeerId,
    val messageText: MessageText
): VkOutputPortRequest

class VkSendMessageOutputResponse: VkOutputPortResponse