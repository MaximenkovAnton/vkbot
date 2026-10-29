package com.simarel.vkbot.ai.command.answer

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortResponse
import com.simarel.vkbot.ai.port.output.context.ConversationContextOutputPort
import com.simarel.vkbot.ai.port.output.context.ConversationContextRequest
import com.simarel.vkbot.ai.port.output.context.ConversationContextResponse
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class GenerateAnswerCommandImplTest {

    private val incomingMessage = FakeVoProvider.createMessage()
    private val user = FakeModelProvider.createVkUserProfile(id = 1)
    private val group = FakeModelProvider.createVkGroupProfile(id = -2)
    private val context = FakeAnswerProvider.createConversationContextResponse(
        currentMessage = incomingMessage,
        chatHistory = listOf(FakeAnswerProvider.createChatMessage(id = 10, fromId = 1, text = "previous")),
        userProfiles = mapOf(user.id to user),
        groupProfiles = mapOf(group.id to group),
    )

    @Test
    fun `execute loads the conversation context and forwards it to the ai port`() {
        val contextPort = RecordingConversationContextPort(context)
        val aiPort = RecordingGenerateAnswerOutputPort("answer")
        val command = GenerateAnswerCommandImpl(aiPort, contextPort)

        command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(incomingMessage))

        assertEquals(incomingMessage, contextPort.requests.single().message)
        assertEquals(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
                currentMessage = context.currentMessage,
                chatHistory = context.chatHistory,
                userProfiles = context.userProfiles,
                groupProfiles = context.groupProfiles,
            ),
            aiPort.requests.single(),
        )
    }

    @Test
    fun `execute returns the ai answer as message text`() {
        val command = GenerateAnswerCommandImpl(
            RecordingGenerateAnswerOutputPort("answer from model"),
            RecordingConversationContextPort(context),
        )

        val response = command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(incomingMessage))

        assertEquals("answer from model", response.responseText.value)
    }

    private class RecordingConversationContextPort(
        private val response: ConversationContextResponse,
    ) : ConversationContextOutputPort {
        val requests = ConcurrentLinkedQueue<ConversationContextRequest>()

        override fun execute(request: ConversationContextRequest): ConversationContextResponse {
            requests.add(request)
            return response
        }
    }

    private class RecordingGenerateAnswerOutputPort(
        private val answer: String,
    ) : GenerateAnswerOutputPort {
        val requests = ConcurrentLinkedQueue<GenerateAnswerOutputPortRequest>()

        override fun execute(request: GenerateAnswerOutputPortRequest): GenerateAnswerOutputPortResponse {
            requests.add(request)
            return FakeAnswerProvider.createGenerateAnswerOutputPortResponse(answer)
        }
    }
}
