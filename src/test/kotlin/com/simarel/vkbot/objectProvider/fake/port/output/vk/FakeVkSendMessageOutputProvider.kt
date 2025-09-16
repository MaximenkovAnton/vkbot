package com.simarel.vkbot.objectProvider.fake.port.output.vk

import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.vkFacade.port.output.vk.VkSendMessageOutputRequest

object FakeVkSendMessageOutputProvider {
    fun createRequest(peerId: Long? = null, messageText: String? = null) = VkSendMessageOutputRequest(
        peerId = FakeVoProvider.createPeerId(peerId),
        messageText = FakeVoProvider.createMessageText(messageText),
    )
}