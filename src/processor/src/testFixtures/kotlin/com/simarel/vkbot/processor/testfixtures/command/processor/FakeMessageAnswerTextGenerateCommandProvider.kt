package com.simarel.vkbot.processor.testfixtures.command.processor

import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeMessageAnswerTextGenerateCommandProvider {
    fun createRequest(message: Message? = null) = MessageAnswerTextGenerateCommandRequest(
        message = message ?: FakeMessageProvider.createMessage(
            fromId = FakeVoProvider.createHumanFromId(),
            messageText = FakeVoProvider.createMessageText("Test message for @simarel"),
        ),
    )
}
