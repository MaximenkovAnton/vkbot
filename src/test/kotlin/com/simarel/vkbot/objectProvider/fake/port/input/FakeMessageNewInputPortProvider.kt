package com.simarel.vkbot.objectProvider.fake.port.input

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeMessageNewInputPortProvider {
    fun createRequest(message: Message? = null) = MessageNewInputPortRequest(
        message = message ?: FakeMessageProvider.createMessage(),
    )
}
