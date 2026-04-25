package com.simarel.vkbot.processor.testfixtures.port.input

import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeMessageNewInputPortProvider {
    fun createRequest(message: Message? = null) = MessageReceivedInputPortRequest(
        message = message ?: FakeMessageProvider.createMessage(
            fromId = FakeVoProvider.createHumanFromId(),
            messageText = FakeVoProvider.createMessageText("Test message for @simarel"),
        ),
    )
}
