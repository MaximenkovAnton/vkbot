package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.testfixtures.command.vkFacade.FakeSendVkMessageCommand
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import com.simarel.vkbot.vkFacade.domain.RandomId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MessageSendUsecaseTest {

    @Test
    fun `execute sends message successfully`() {
        // Given
        val sendCommand = FakeSendVkMessageCommand()
        val usecase = MessageSendUsecase(sendCommand)
        val peerId = FakeVoProvider.createPeerId()
        val messageText = FakeVoProvider.createMessageText()
        val request = FakeVkFacadeProvider.createVkSendMessageInputRequest(
            responseMessage = FakeModelProvider.createResponseMessage(
                messageText = messageText,
                responseTo = FakeVoProvider.createMessage(peerId = peerId),
            ),
        )

        // When
        usecase.execute(request)

        // Then
        assertEquals(1, sendCommand.executeCalls.size)
        val commandRequest = sendCommand.executeCalls.first()
        assertEquals(peerId, commandRequest.peerId)
        assertEquals(messageText, commandRequest.message)
    }

    @Test
    fun `execute takes the random id from the inbox id`() {
        val sendCommand = FakeSendVkMessageCommand()
        val usecase = MessageSendUsecase(sendCommand)
        val inboxMessageId = FakeVoProvider.createInboxMessageId(value = 42)
        val request = FakeVkFacadeProvider.createVkSendMessageInputRequest(
            responseMessage = FakeModelProvider.createResponseMessage(
                responseTo = FakeVoProvider.createMessage(inboxMessageId = inboxMessageId),
            ),
        )

        usecase.execute(request)
        usecase.execute(request)

        val randomIds = sendCommand.executeCalls.map { it.rand }
        assertEquals(RandomId.ofInbox(inboxMessageId).value, randomIds.first())
        assertEquals(1, randomIds.distinct().size)
        assertNotEquals(0, randomIds.first())
    }

    @Test
    fun `execute falls back to the deterministic random id without an inbox id`() {
        val sendCommand = FakeSendVkMessageCommand()
        val usecase = MessageSendUsecase(sendCommand)
        val peerId = FakeVoProvider.createPeerId()
        val messageText = FakeVoProvider.createMessageText()
        val request = FakeVkFacadeProvider.createVkSendMessageInputRequest(
            responseMessage = FakeModelProvider.createResponseMessage(
                messageText = messageText,
                responseTo = FakeVoProvider.createMessage(peerId = peerId, messageText = messageText),
            ),
        )

        usecase.execute(request)
        usecase.execute(request)

        val randomIds = sendCommand.executeCalls.map { it.rand }
        assertEquals(RandomId.of(peerId, messageText).value, randomIds.first())
        assertEquals(1, randomIds.distinct().size)
        assertNotEquals(0, randomIds.first())
    }
}
