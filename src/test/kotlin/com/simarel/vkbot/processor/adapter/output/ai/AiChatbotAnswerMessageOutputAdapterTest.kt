package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.objectProvider.fake.service.FakeUserAnswerAiService
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AiChatbotAnswerMessageOutputAdapterTest {

    @Test
    fun `execute calls user answer AI service with message text`() {
        // Given
        val fakeAiService = FakeUserAnswerAiService()
        val adapter = AiChatbotAnswerMessageOutputAdapter(fakeAiService)
        val message = FakeMessageProvider.createMessage()
        val request = AiChatbotAnswerMessageOutputPortRequest(message)

        // When
        val result = adapter.execute(request)

        // Then
        assertEquals(1, fakeAiService.answerUserCalls.size)
        assertEquals(message.messageText.value, fakeAiService.answerUserCalls.first())
        assertNotNull(result)
        assertEquals(AiChatbotAnswerMessageOutputPortResponse::class, result::class)
    }
}
