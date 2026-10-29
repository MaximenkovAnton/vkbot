package com.simarel.vkbot.ai.adapter.output.context

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.ai.testfixtures.FakeConversationContextPortProvider
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBeforePort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

class ConversationContextRestAdapterTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create().registerModule(JavaTimeModule())

    @Test
    fun `execute requests the last twenty messages before the current message`() {
        val findMessagesPort = RecordingFindMessagesBeforePort()
        val message = FakeVoProvider.createMessage(
            peerId = FakeVoProvider.createPeerId(100),
            conversationMessageId = FakeVoProvider.createConversationMessageId(50),
        )

        createAdapter(findMessagesPort = findMessagesPort)
            .execute(FakeAnswerProvider.createConversationContextRequest(message))

        assertEquals(
            FakePersistencePortProvider.createFindMessagesBeforeRequest(
                peerId = 100,
                beforeConversationMessageId = 50,
                limit = 20,
            ),
            findMessagesPort.requests.single(),
        )
    }

    @Test
    fun `execute maps stored messages into chat history and keeps the current message`() {
        val storedMessage = FakeModelProvider.createStoredMessage(
            conversationMessageId = 33,
            fromId = 5,
            messageText = "history text",
            date = FIXED_DATE,
        )
        val message = FakeVoProvider.createMessage()

        val response = createAdapter(findMessagesPort = RecordingFindMessagesBeforePort(listOf(storedMessage)))
            .execute(FakeAnswerProvider.createConversationContextRequest(message))

        assertEquals(message, response.currentMessage)
        assertEquals(
            listOf(
                FakeAnswerProvider.createChatMessage(
                    id = 33,
                    fromId = 5,
                    text = "history text",
                    date = FIXED_DATE,
                ),
            ),
            response.chatHistory,
        )
    }

    @Test
    fun `execute maps a stored message without text to an empty chat message text`() {
        val storedMessage = FakeModelProvider.createStoredMessage(messageText = null)

        val response = createAdapter(findMessagesPort = RecordingFindMessagesBeforePort(listOf(storedMessage)))
            .execute(FakeAnswerProvider.createConversationContextRequest())

        assertEquals("", response.chatHistory.single().text.value)
    }

    @Test
    fun `execute asks user profiles for human authors and group profiles for group authors`() {
        val storedMessages = listOf(
            FakeModelProvider.createStoredMessage(conversationMessageId = 10, fromId = 5),
            FakeModelProvider.createStoredMessage(conversationMessageId = 11, fromId = -8),
        )
        val message = FakeVoProvider.createMessage(fromId = FakeVoProvider.createFromId(7))
        val findUserProfilesPort = RecordingFindUserProfilesPort()
        val findGroupProfilesPort = RecordingFindGroupProfilesPort()

        createAdapter(
            findMessagesPort = RecordingFindMessagesBeforePort(storedMessages),
            findUserProfilesPort = findUserProfilesPort,
            findGroupProfilesPort = findGroupProfilesPort,
        ).execute(FakeAnswerProvider.createConversationContextRequest(message))

        assertEquals(setOf(5L, 7L), findUserProfilesPort.requests.single().ids.map { it.value }.toSet())
        assertEquals(setOf(-8L), findGroupProfilesPort.requests.single().ids.map { it.value }.toSet())
    }

    @Test
    fun `execute associates returned profiles with their author ids`() {
        val user = FakeModelProvider.createVkUserProfile(id = 7)
        val group = FakeModelProvider.createVkGroupProfile(id = -8)
        val message = FakeVoProvider.createMessage(fromId = FakeVoProvider.createFromId(7))

        val response = createAdapter(
            findUserProfilesPort = RecordingFindUserProfilesPort(listOf(user)),
            findGroupProfilesPort = RecordingFindGroupProfilesPort(listOf(group)),
        ).execute(FakeAnswerProvider.createConversationContextRequest(message))

        assertEquals(mapOf(user.id to user), response.userProfiles)
        assertEquals(mapOf(group.id to group), response.groupProfiles)
    }

    @Test
    fun `execute parses a forwarded message array recursively and collects nested authors`() {
        val nestedAuthor = FakeVoProvider.createFromId(-8)
        val nested = FakeVoProvider.createMessage(
            fromId = nestedAuthor,
            messageText = FakeVoProvider.createMessageText("nested"),
        )
        val forwarded = FakeVoProvider.createMessage(
            fromId = FakeVoProvider.createFromId(5),
            messageText = FakeVoProvider.createMessageText("forwarded"),
            forwardedMessages = listOf(nested),
        )
        val storedMessage = FakeModelProvider.createStoredMessage(
            fromId = 30,
            forwardedMessages = objectMapper.writeValueAsString(listOf(forwarded)),
        )
        val message = FakeVoProvider.createMessage(fromId = FakeVoProvider.createFromId(7))
        val findUserProfilesPort = RecordingFindUserProfilesPort()
        val findGroupProfilesPort = RecordingFindGroupProfilesPort()

        val response = createAdapter(
            findMessagesPort = RecordingFindMessagesBeforePort(listOf(storedMessage)),
            findUserProfilesPort = findUserProfilesPort,
            findGroupProfilesPort = findGroupProfilesPort,
        ).execute(FakeAnswerProvider.createConversationContextRequest(message))

        val parsed = response.chatHistory.single().forwardedMessages.single()
        assertEquals("forwarded", parsed.messageText.value)
        assertEquals("nested", parsed.forwardedMessages.single().messageText.value)
        assertEquals(setOf(5L, 7L, 30L), findUserProfilesPort.requests.single().ids.map { it.value }.toSet())
        assertEquals(setOf(-8L), findGroupProfilesPort.requests.single().ids.map { it.value }.toSet())
    }

    @Test
    fun `execute parses a single forwarded message object`() {
        val forwarded = FakeVoProvider.createMessage(
            messageText = FakeVoProvider.createMessageText("single forwarded"),
        )
        val storedMessage = FakeModelProvider.createStoredMessage(
            forwardedMessages = objectMapper.writeValueAsString(forwarded),
        )

        val response = createAdapter(findMessagesPort = RecordingFindMessagesBeforePort(listOf(storedMessage)))
            .execute(FakeAnswerProvider.createConversationContextRequest())

        assertEquals(
            "single forwarded",
            response.chatHistory.single().forwardedMessages.single().messageText.value,
        )
    }

    private fun createAdapter(
        findMessagesPort: FindStoredMessagesBeforePort = RecordingFindMessagesBeforePort(),
        findUserProfilesPort: FindStoredUserProfilesByIdsPort = RecordingFindUserProfilesPort(),
        findGroupProfilesPort: FindStoredGroupProfilesByIdsPort = RecordingFindGroupProfilesPort(),
    ): ConversationContextRestAdapter = ConversationContextRestAdapter(
        findMessagesBeforePort = findMessagesPort,
        findUserProfilesByIdsPort = findUserProfilesPort,
        findGroupProfilesByIdsPort = findGroupProfilesPort,
        objectMapper = objectMapper,
    )

    private class RecordingFindMessagesBeforePort(
        private val messages: List<StoredMessage> = emptyList(),
    ) : FindStoredMessagesBeforePort {
        val requests = ConcurrentLinkedQueue<FindStoredMessagesBeforePort.Request>()

        override fun execute(request: FindStoredMessagesBeforePort.Request): FindStoredMessagesBeforePort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindMessagesBeforeResponse(messages)
        }
    }

    private class RecordingFindUserProfilesPort(
        private val profiles: List<VkUserProfile> = emptyList(),
    ) : FindStoredUserProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredUserProfilesByIdsPort.Request,
        ): FindStoredUserProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindUserProfilesByIdsResponse(profiles)
        }
    }

    private class RecordingFindGroupProfilesPort(
        private val profiles: List<VkGroupProfile> = emptyList(),
    ) : FindStoredGroupProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredGroupProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredGroupProfilesByIdsPort.Request,
        ): FindStoredGroupProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindGroupProfilesByIdsResponse(profiles)
        }
    }
}
