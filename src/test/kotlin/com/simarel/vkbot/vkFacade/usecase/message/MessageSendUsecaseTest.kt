package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.objectProvider.fake.command.vkFacade.FakeSendVkMessageCommand
import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageSendUsecaseTest {

    @Test
    fun `execute sends message successfully`() {
        // Given
        val sendCommand = FakeSendVkMessageCommand()
        val usecase = MessageSendUsecase(sendCommand)
        val peerId = FakeVoProvider.createPeerId()
        val messageText = FakeVoProvider.createMessageText()
        val request = VkSendMessageInputRequest(peerId, messageText)
        val expectedResult = VkSendMessageInputResponse()

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals(1, sendCommand.executeCalls.size)
        val commandRequest = sendCommand.executeCalls.first()
        assertEquals(peerId, commandRequest.peerId)
        assertEquals(messageText, commandRequest.message)
        assertEquals(expectedResult, result)
    }
}
