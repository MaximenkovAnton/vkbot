package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakePublishVkEventCommandProvider {

    fun createRequest(
        message: Message? = null,
    ) = PublishVkEventCommandRequest(message = message ?: FakeVoProvider.createMessage())
}
