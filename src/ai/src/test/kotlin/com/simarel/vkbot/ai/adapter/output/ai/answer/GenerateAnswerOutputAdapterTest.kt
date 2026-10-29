package com.simarel.vkbot.ai.adapter.output.ai.answer

import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class GenerateAnswerOutputAdapterTest {

    private val user = FakeModelProvider.createVkUserProfile(
        id = 1,
        firstName = "Ivan",
        lastName = "Petrov",
        screenName = "ivan_petrov",
    )
    private val group = FakeModelProvider.createVkGroupProfile(
        id = -2,
        name = "Group",
        screenName = "group",
    )

    @Test
    fun `execute formats user and group profiles`() {
        val aiService = RecordingUserAnswerAiService()

        GenerateAnswerOutputAdapter(aiService).execute(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
                userProfiles = mapOf(user.id to user),
                groupProfiles = mapOf(group.id to group),
            ),
        )

        assertEquals("- id1: Ivan Petrov (@ivan_petrov)", aiService.userProfiles.single())
        assertEquals("- id-2: Group (@group)", aiService.groupProfiles.single())
    }

    @Test
    fun `execute formats chat history as message blocks`() {
        val aiService = RecordingUserAnswerAiService()

        GenerateAnswerOutputAdapter(aiService).execute(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
                chatHistory = listOf(FakeAnswerProvider.createChatMessage(id = 10, fromId = 1, text = "hello")),
                userProfiles = mapOf(user.id to user),
            ),
        )

        assertEquals(messageBlock(10, "Ivan Petrov (id1)", "hello"), aiService.chatHistory.single())
    }

    @Test
    fun `execute formats the current message and nested forwarded blocks with levels and indentation`() {
        val nested = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(1),
            messageText = FakeVoProvider.createMessageText("nested"),
        )
        val forwarded = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(-2),
            messageText = FakeVoProvider.createMessageText("forwarded"),
            forwardedMessages = listOf(nested),
        )
        val current = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(1),
            conversationMessageId = FakeVoProvider.createConversationMessageId(50),
            messageText = FakeVoProvider.createMessageText("current"),
            forwardedMessages = listOf(forwarded),
        )
        val aiService = RecordingUserAnswerAiService()

        GenerateAnswerOutputAdapter(aiService).execute(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
                currentMessage = current,
                chatHistory = emptyList(),
                userProfiles = mapOf(user.id to user),
                groupProfiles = mapOf(group.id to group),
            ),
        )

        assertEquals(
            listOf(
                "[MESSAGE_START]",
                "ID: 50 | Отправитель: Ivan Petrov (id1)",
                "Текст: \"current",
                "[FORWARDED_BLOCK]",
                "Уровень: 1 | Отправитель: Group (id-2)",
                "Текст: \"forwarded",
                "    [FORWARDED_BLOCK]",
                "    Уровень: 2 | Отправитель: Ivan Petrov (id1)",
                "Текст: \"nested",
                "    [END_LEVEL_2]",
                "[END_LEVEL_1]",
                "[MESSAGE_END]",
            ).joinToString("\n"),
            aiService.currentMessages.single(),
        )
    }

    @Test
    fun `execute falls back to id when a profile is missing`() {
        val nested = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(-555),
            messageText = FakeVoProvider.createMessageText("unknown group"),
        )
        val current = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(999),
            conversationMessageId = FakeVoProvider.createConversationMessageId(50),
            messageText = FakeVoProvider.createMessageText("current"),
            forwardedMessages = listOf(nested),
        )
        val aiService = RecordingUserAnswerAiService()

        GenerateAnswerOutputAdapter(aiService).execute(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(
                currentMessage = current,
                chatHistory = emptyList(),
            ),
        )

        assertEquals(
            listOf(
                "[MESSAGE_START]",
                "ID: 50 | Отправитель: id999",
                "Текст: \"current",
                "[FORWARDED_BLOCK]",
                "Уровень: 1 | Отправитель: id-555",
                "Текст: \"unknown group",
                "[END_LEVEL_1]",
                "[MESSAGE_END]",
            ).joinToString("\n"),
            aiService.currentMessages.single(),
        )
    }

    @Test
    fun `execute passes empty profile blocks when no profiles are known`() {
        val aiService = RecordingUserAnswerAiService()

        GenerateAnswerOutputAdapter(aiService).execute(
            FakeAnswerProvider.createGenerateAnswerOutputPortRequest(chatHistory = emptyList()),
        )

        assertEquals("", aiService.userProfiles.single())
        assertEquals("", aiService.groupProfiles.single())
        assertEquals("", aiService.chatHistory.single())
    }

    @Test
    fun `execute returns the answer produced by the ai service`() {
        val aiService = RecordingUserAnswerAiService(answer = "model answer")

        val response = GenerateAnswerOutputAdapter(aiService)
            .execute(FakeAnswerProvider.createGenerateAnswerOutputPortRequest())

        assertEquals("model answer", response.answer)
    }

    private fun messageBlock(id: Long, author: String, text: String): String = listOf(
        "[MESSAGE_START]",
        "ID: $id | Отправитель: $author",
        "Текст: \"$text",
        "[MESSAGE_END]",
    ).joinToString("\n")

    private class RecordingUserAnswerAiService(
        private val answer: String = "model answer",
    ) : UserAnswerAiService {
        val userProfiles = ConcurrentLinkedQueue<String>()
        val groupProfiles = ConcurrentLinkedQueue<String>()
        val chatHistory = ConcurrentLinkedQueue<String>()
        val currentMessages = ConcurrentLinkedQueue<String>()

        override fun generateAnswer(
            userProfiles: String,
            groupProfiles: String,
            chatHistory: String,
            currentMessage: String,
            dateTime: String,
        ): String {
            this.userProfiles.add(userProfiles)
            this.groupProfiles.add(groupProfiles)
            this.chatHistory.add(chatHistory)
            this.currentMessages.add(currentMessage)
            return answer
        }
    }
}
