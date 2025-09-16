package com.simarel.vkbot.objectProvider.fake.command.processor

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeMessageAnswerTextGenerateCommandProvider {
    fun createRequest(message: Message? = null) =
        MessageAnswerTextGenerateCommandRequest(message ?: FakeMessageProvider.createMessage())
}
