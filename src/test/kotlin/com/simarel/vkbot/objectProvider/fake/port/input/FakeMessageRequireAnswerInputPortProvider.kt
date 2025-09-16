package com.simarel.vkbot.objectProvider.fake.port.input

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeMessageRequireAnswerInputPortProvider {
    fun createRequest(message: Message? = null) =
        MessageRequireAnswerInputPortRequest(message ?: FakeMessageProvider.createMessage())
}
