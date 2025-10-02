package com.simarel.vkbot.objectProvider.fake.command.vkFacade

import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest

object FakeSendVkMessageCommandProvider {
    fun createRequest(
        peerId: PeerId? = null,
        messageText: MessageText? = null,
    ) = SendVkMessageCommandRequest(
        peerId ?: FakeVoProvider.createPeerId(),
        messageText ?: FakeVoProvider.createMessageText(),
    )
}
