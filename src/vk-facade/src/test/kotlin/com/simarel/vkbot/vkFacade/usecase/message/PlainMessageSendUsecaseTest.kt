package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.testfixtures.command.vkFacade.FakeSendVkMessageCommand
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.vkFacade.FakeVkFacadeProvider
import com.simarel.vkbot.vkFacade.domain.RandomId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlainMessageSendUsecaseTest {

    private val peerId = FakeVoProvider.createPeerId(value = 2_000_000_001)
    private val messageText = FakeVoProvider.createMessageText(value = "С Новым годом!")
    private val request = FakeVkFacadeProvider.createVkSendPlainMessageInputRequest(
        peerId = peerId,
        messageText = messageText,
    )

    @Test
    fun `execute sends the message without forward`() {
        val command = FakeSendVkMessageCommand()

        PlainMessageSendUsecase(command).execute(request)

        val commandRequest = command.executeCalls.single()
        assertEquals(peerId, commandRequest.peerId)
        assertEquals(messageText, commandRequest.message)
        assertNull(commandRequest.forwardedMessages)
    }

    @Test
    fun `execute sends the same non-zero random id for the same peer and text`() {
        val command = FakeSendVkMessageCommand()

        PlainMessageSendUsecase(command).execute(request)
        PlainMessageSendUsecase(command).execute(request)

        val randomIds = command.executeCalls.map { it.rand }
        assertEquals(RandomId.of(peerId, messageText).value, randomIds.first())
        assertEquals(1, randomIds.distinct().size)
        assertNotEquals(0, randomIds.first())
    }
}
