package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.objectProvider.fake.service.FakeUserAnswerAiService
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AiChatbotAnswerMessageOutputAdapterTest {

    @Test
    fun `execute calls user answer AI service with message object`() {
        // Given
        val fakeAiService = FakeUserAnswerAiService()
        val adapter = AiChatbotAnswerMessageOutputAdapter(fakeAiService)
        val message = FakeVoProvider.createMessage()
        val request = AiChatbotAnswerMessageOutputPortRequest(message)

        // When
        val result = adapter.execute(request)

        // Then
        assertEquals(1, fakeAiService.answerUserMessageCalls.size)
        val calledMessage = fakeAiService.answerUserMessageCalls.first()
        assertEquals(message.messageText.value, calledMessage.messageText.value)
        assertNotNull(result)
        assertEquals(AiChatbotAnswerMessageOutputPortResponse::class, result::class)
    }

    @Test
    fun `execute calls user answer AI service with forwarded messages`() {
        // Given
        val fakeAiService = FakeUserAnswerAiService()
        val adapter = AiChatbotAnswerMessageOutputAdapter(fakeAiService)
        val message = FakeVoProvider.createMessageWithForwarded()
        val request = AiChatbotAnswerMessageOutputPortRequest(message)

        // When
        val result = adapter.execute(request)

        // Then
        assertEquals(1, fakeAiService.answerUserMessageCalls.size)
        val calledMessage = fakeAiService.answerUserMessageCalls.first()
        assertEquals(message.messageText.value, calledMessage.messageText.value)
        assertEquals(2, calledMessage.forwardedMessages.size)
        assertNotNull(result)
        assertEquals(AiChatbotAnswerMessageOutputPortResponse::class, result::class)
    }
}
