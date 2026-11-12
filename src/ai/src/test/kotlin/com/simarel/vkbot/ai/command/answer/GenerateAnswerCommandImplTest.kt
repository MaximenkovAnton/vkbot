package com.simarel.vkbot.ai.command.answer

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortResponse
import com.simarel.vkbot.ai.port.output.context.ConversationContextOutputPort
import com.simarel.vkbot.ai.port.output.context.ConversationContextRequest
import com.simarel.vkbot.ai.port.output.context.ConversationContextResponse
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindUserKnowledgePort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level

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
        val command = GenerateAnswerCommandImpl(aiPort, contextPort, FakeFindUserKnowledgePort())

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
            FakeFindUserKnowledgePort(),
        )

        val response = command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(incomingMessage))

        assertEquals("answer from model", response.responseText.value)
    }

    @Test
    fun `execute reads knowledge for the current message author`() {
        val author = FakeVoProvider.createFromId(7)
        val message = FakeVoProvider.createMessage(fromId = author)
        val knowledge = FakeModelProvider.createUserKnowledge(
            facts = listOf(FakeModelProvider.createUserFact(value = "футбол")),
            summary = "Свежая сводка профиля",
            summaryFresh = true,
        )
        val knowledgePort = FakeFindUserKnowledgePort().apply { this.knowledge = mapOf(author to knowledge) }
        val aiPort = RecordingGenerateAnswerOutputPort("answer")
        val command = GenerateAnswerCommandImpl(
            aiPort,
            RecordingConversationContextPort(
                FakeAnswerProvider.createConversationContextResponse(currentMessage = message),
            ),
            knowledgePort,
        )

        command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(message))

        assertEquals(listOf(author), knowledgePort.executeCalls.single().userIds)
        assertEquals(knowledge, aiPort.requests.single().userKnowledge)
    }

    @Test
    fun `execute passes empty knowledge when the port has no entry for the author`() {
        val knowledgePort = FakeFindUserKnowledgePort()
        val aiPort = RecordingGenerateAnswerOutputPort("answer")
        val command = GenerateAnswerCommandImpl(
            aiPort,
            RecordingConversationContextPort(context),
            knowledgePort,
        )

        command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(incomingMessage))

        assertEquals(FakeModelProvider.createUserKnowledge(), aiPort.requests.single().userKnowledge)
    }

    @Test
    fun `execute fails open with a warning without pii when the knowledge read fails`() {
        val message = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(7),
            peerId = FakeVoProvider.createPeerId(200),
            messageText = FakeVoProvider.createMessageText("секрет"),
        )
        val profile = FakeModelProvider.createVkUserProfile(id = 7, firstName = "Иван", lastName = "Иванов")
        val contextPort = RecordingConversationContextPort(
            FakeAnswerProvider.createConversationContextResponse(
                currentMessage = message,
                userProfiles = mapOf(profile.id to profile),
            ),
        )
        val knowledgePort = FakeFindUserKnowledgePort().apply {
            failure = IllegalStateException("knowledge failed for Иван Иванов")
        }
        val aiPort = RecordingGenerateAnswerOutputPort("answer from model")
        val command = GenerateAnswerCommandImpl(aiPort, contextPort, knowledgePort)
        val loggerCapture = SystemLoggerCapture(GenerateAnswerCommandImpl::class.java.name)

        try {
            val response = command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(message))

            assertEquals("answer from model", response.responseText.value)
            assertEquals(FakeModelProvider.createUserKnowledge(), aiPort.requests.single().userKnowledge)
            assertEquals(
                listOf("User knowledge read failed for peerId=200: IllegalStateException"),
                loggerCapture.messages(Level.WARNING),
            )
            val logged = loggerCapture.records.mapNotNull { it.message }
            assertTrue(logged.none { "секрет" in it || "Иван" in it || "футбол" in it })
        } finally {
            loggerCapture.close()
        }
    }

    @Test
    fun `execute restores the interrupt flag and rethrows InterruptedException`() {
        val aiPort = RecordingGenerateAnswerOutputPort("answer")
        val command = GenerateAnswerCommandImpl(
            aiPort,
            RecordingConversationContextPort(context),
            InterruptingFindUserKnowledgePort(),
        )
        val loggerCapture = SystemLoggerCapture(GenerateAnswerCommandImpl::class.java.name)

        try {
            assertThrows(InterruptedException::class.java) {
                command.execute(FakeAnswerProvider.createGenerateAnswerCommandRequest(incomingMessage))
            }

            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(aiPort.requests.isEmpty())
            assertEquals(emptyList<String>(), loggerCapture.messages(Level.WARNING))
        } finally {
            Thread.interrupted()
            loggerCapture.close()
        }
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

    private class InterruptingFindUserKnowledgePort : FindUserKnowledgePort {
        override fun execute(
            request: FindUserKnowledgePort.Request,
        ): FindUserKnowledgePort.Response = throw InterruptedException("interrupted")
    }
}
