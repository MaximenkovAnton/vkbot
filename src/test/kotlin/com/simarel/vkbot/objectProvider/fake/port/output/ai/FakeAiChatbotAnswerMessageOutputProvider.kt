package com.simarel.vkbot.objectProvider.fake.port.output.ai

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeAiChatbotAnswerMessageOutputProvider {
    fun createRequest(message: Message? = null) = AiChatbotAnswerMessageOutputPortRequest(message ?: FakeMessageProvider.createMessage())
}
