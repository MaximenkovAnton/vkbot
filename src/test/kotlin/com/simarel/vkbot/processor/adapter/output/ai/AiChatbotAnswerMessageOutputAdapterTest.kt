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
    fun `execute calls user answer AI service with message text and context`() {
        // Given
        val fakeAiService = FakeUserAnswerAiService()
        val adapter = AiChatbotAnswerMessageOutputAdapter(fakeAiService)
        val message = FakeVoProvider.createMessage()
        val request = AiChatbotAnswerMessageOutputPortRequest(message)

        // When
        val result = adapter.execute(request)

        // Then
        assertEquals(1, fakeAiService.answerUserCalls.size)
        val call = fakeAiService.answerUserCalls.first()
        assertEquals(message.messageText.value, call.first)
        assertNotNull(result)
        assertEquals(AiChatbotAnswerMessageOutputPortResponse::class, result::class)
    }

    @Test
    fun `execute calls user answer AI service with forwarded messages context`() {
        // Given
        val fakeAiService = FakeUserAnswerAiService()
        val adapter = AiChatbotAnswerMessageOutputAdapter(fakeAiService)
        val message = FakeVoProvider.createMessageWithForwarded()
        val request = AiChatbotAnswerMessageOutputPortRequest(message)

        // When
        val result = adapter.execute(request)

        // Then
        assertEquals(1, fakeAiService.answerUserCalls.size)
        val call = fakeAiService.answerUserCalls.first()
        assertEquals(message.messageText.value, call.first)
        assert(call.second.contains("[Пересланные сообщения]"))
        assert(call.second.contains("[11111]: forwarded message 1"))
        assert(call.second.contains("[22222]: forwarded message 2"))
        assertNotNull(result)
        assertEquals(AiChatbotAnswerMessageOutputPortResponse::class, result::class)
    }
}
