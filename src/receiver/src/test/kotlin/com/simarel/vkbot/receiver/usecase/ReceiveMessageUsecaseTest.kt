package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.fixtures.FakePublishVkEventCommand
import com.simarel.vkbot.receiver.fixtures.FakeVkUpdateInputPortProvider
import com.simarel.vkbot.receiver.fixtures.port.FakeRegisterIncomingMessagePort
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortResponse
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiveMessageUsecaseTest {

    @Test
    fun `registers the incoming message before publishing it`() {
        val journal = mutableListOf<String>()
        val register = FakeRegisterIncomingMessagePort(journal = journal)
        val publishCommand = FakePublishVkEventCommand(journal = journal)
        val usecase = ReceiveMessageUsecase(register, publishCommand)
        val message = createMessage()

        val result = usecase.execute(
            FakeVkUpdateInputPortProvider.createRequest(
                type = VkUpdateType.MESSAGE_NEW,
                message = message,
                rawUpdate = RAW_UPDATE,
            ),
        )

        assertEquals(VkUpdateInputPortResponse, result)
        assertEquals(listOf("register", "publish"), journal)
        assertEquals(PEER_ID, register.executeCalls.single().peerId.value)
        assertEquals(CONVERSATION_MESSAGE_ID, register.executeCalls.single().conversationMessageId.value)
        assertEquals(RAW_UPDATE, register.executeCalls.single().payload)
        assertEquals(1, publishCommand.executeCalls.size)
    }

    @Test
    fun `publishes the message with the registered inbox id`() {
        val register = FakeRegisterIncomingMessagePort(
            response = FakePersistencePortProvider.createRegisterIncomingMessageResponse(
                inboxMessageId = INBOX_MESSAGE_ID,
            ),
        )
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(register, publishCommand)
        val message = createMessage()

        usecase.execute(FakeVkUpdateInputPortProvider.createRequest(message = message))

        val published = publishCommand.executeCalls.single().message
        assertEquals(INBOX_MESSAGE_ID, published.inboxMessageId?.value)
        assertEquals(message, published.copy(inboxMessageId = null))
        assertEquals(1, publishCommand.executeCalls.size)
    }

    @Test
    fun `register failure propagates and publishes nothing`() {
        val register = FakeRegisterIncomingMessagePort()
        register.failure = IllegalStateException(REGISTER_FAILURE)
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(register, publishCommand)

        val thrown = assertThrows(IllegalStateException::class.java) {
            usecase.execute(FakeVkUpdateInputPortProvider.createRequest(message = createMessage()))
        }

        assertEquals(REGISTER_FAILURE, thrown.message)
        assertEquals(1, register.executeCalls.size)
        assertTrue(publishCommand.executeCalls.isEmpty())
    }

    @Test
    fun `execute without a message publishes nothing`() {
        val register = FakeRegisterIncomingMessagePort()
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(register, publishCommand)

        val result = usecase.execute(FakeVkUpdateInputPortProvider.createRequest(type = VkUpdateType.UNKNOWN))

        assertEquals(VkUpdateInputPortResponse, result)
        assertTrue(register.executeCalls.isEmpty())
        assertTrue(publishCommand.executeCalls.isEmpty())
    }

    private fun createMessage() = FakeVoProvider.createMessage(
        peerId = FakeVoProvider.createPeerId(PEER_ID),
        conversationMessageId = FakeVoProvider.createConversationMessageId(CONVERSATION_MESSAGE_ID),
    )

    private companion object {
        const val PEER_ID = 101L
        const val CONVERSATION_MESSAGE_ID = 202L
        const val INBOX_MESSAGE_ID = 42L
        const val REGISTER_FAILURE = "inbox is down"
        const val RAW_UPDATE =
            """{"type":"message_new","object":{"message":{"text":"hello"}},"group_id":1}"""
    }
}
