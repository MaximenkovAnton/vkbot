package com.simarel.vkbot.testfixtures.port.output.vk

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.vkFacade.port.output.vk.VkSendMessageOutputRequest

object FakeVkSendMessageOutputProvider {
    fun createRequest(
        peerId: Long? = null,
        messageText: String? = null,
    ) = VkSendMessageOutputRequest(
        peerId = FakeVoProvider.createPeerId(peerId),
        messageText = FakeVoProvider.createMessageText(messageText),
    )
}
