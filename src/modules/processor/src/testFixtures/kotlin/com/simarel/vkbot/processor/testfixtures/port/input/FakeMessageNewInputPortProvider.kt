package com.simarel.vkbot.processor.testfixtures.port.input

import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeMessageNewInputPortProvider {
    fun createRequest(message: Message? = null) = MessageNewInputPortRequest(
        message = message ?: FakeMessageProvider.createMessage(
            fromId = FakeVoProvider.createHumanFromId(),
            messageText = FakeVoProvider.createMessageText("Test message for @simarel")
        ),
    )
}
