package com.simarel.vkbot.processor.command.answer

import com.simarel.vkbot.objectProvider.fake.adapter.output.ai.FakeAiChatbotAnswerMessageOutputPort
import com.simarel.vkbot.objectProvider.fake.command.processor.FakeMessageAnswerTextGenerateCommandProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageAnswerTextGenerateCommandImplTest {

    @Test
    fun `execute command successfully`() {
        val aiChatbotPort = FakeAiChatbotAnswerMessageOutputPort()
        val command = MessageAnswerTextGenerateCommandImpl(aiChatbotPort)
        val request = FakeMessageAnswerTextGenerateCommandProvider.createRequest()

        val result = command.execute(request)

        assertEquals(1, aiChatbotPort.executeCalls.size)
        assertEquals(request.message, aiChatbotPort.executeCalls.first().message)
        assertEquals(result.messageText, result.messageText)
    }
}
