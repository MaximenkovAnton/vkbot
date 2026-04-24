package com.simarel.vkbot.testfixtures.command.vkFacade

import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages

object FakeSendVkMessageCommandProvider {
    fun createRequest(
        peerId: PeerId? = null,
        messageText: MessageText? = null,
        forwardedMessages: ForwardedMessages? = null,
        rand: Int = 0
    ) = SendVkMessageCommandRequest(
        peerId = peerId ?: FakeVoProvider.createPeerId(),
        message = messageText ?: FakeVoProvider.createMessageText(),
        forwardedMessages = forwardedMessages ?: FakeVoProvider.createForwardedMessage(),
        rand = rand,
    )
}
