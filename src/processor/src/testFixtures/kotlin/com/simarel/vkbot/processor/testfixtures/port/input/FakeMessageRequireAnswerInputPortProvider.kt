package com.simarel.vkbot.processor.testfixtures.port.input

import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeMessageRequireAnswerInputPortProvider {
    fun createRequest(
        message: Message? = null,
    ) = MessageRequireAnswerInputPortRequest(
        message ?: FakeMessageProvider.createMessage(
            fromId = FakeVoProvider.createHumanFromId(),
            messageText = FakeVoProvider.createMessageText("Test message for @simarel")
        )
    )
}
