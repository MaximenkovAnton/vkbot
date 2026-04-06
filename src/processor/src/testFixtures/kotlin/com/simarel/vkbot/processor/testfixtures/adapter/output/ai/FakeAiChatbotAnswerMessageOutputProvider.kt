package com.simarel.vkbot.processor.testfixtures.adapter.output.ai

import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider

object FakeAiChatbotAnswerMessageOutputProvider {
    fun createRequest(
        message: Message? = null,
    ) = AiChatbotAnswerMessageOutputPortRequest(
        message = message ?: FakeMessageProvider.createMessage(
            fromId = FakeVoProvider.createHumanFromId(),
            messageText = FakeVoProvider.createMessageText("Test message for @simarel")
        ),
    )
}
