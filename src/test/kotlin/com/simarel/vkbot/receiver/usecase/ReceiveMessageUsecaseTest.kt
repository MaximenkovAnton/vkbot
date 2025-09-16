package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.objectProvider.fake.command.receiver.FakePublishVkEventCommand
import com.simarel.vkbot.objectProvider.fake.domain.FakeVkProvider
import com.simarel.vkbot.objectProvider.fake.port.input.FakeVkConfirmationInputPortProvider
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReceiveMessageUsecaseTest {

    @Test
    fun `execute with confirmation event returns confirmation response`() {
        // Given
        val confirmationResponse = FakeVkConfirmationInputPortProvider.createConfirmationResponse()
        val okResponse = FakeVkConfirmationInputPortProvider.createOkResponse()
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(confirmationResponse, okResponse, publishCommand)
        val vkEvent = FakeVkProvider.createVkEvent(VkCallbackEvent.CONFIRMATION)
        val request = FakeVkConfirmationInputPortProvider.createRequest(vkEvent)

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals(confirmationResponse.response.value, result.response.value)
        assertEquals(0, publishCommand.executeCalls.size)
    }

    @Test
    fun `execute with unknown event returns ok response`() {
        // Given
        val confirmationResponse = FakeVkConfirmationInputPortProvider.createConfirmationResponse()
        val okResponse = FakeVkConfirmationInputPortProvider.createOkResponse()
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(confirmationResponse, okResponse, publishCommand)
        val vkEvent = FakeVkProvider.createVkEvent(VkCallbackEvent.UNKNOWN)
        val request = FakeVkConfirmationInputPortProvider.createRequest(vkEvent)

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals(okResponse.response.value, result.response.value)
        assertEquals(0, publishCommand.executeCalls.size)
    }

    @Test
    fun `execute with message_new event publishes event and returns ok`() {
        // Given
        val confirmationResponse = FakeVkConfirmationInputPortProvider.createConfirmationResponse()
        val okResponse = FakeVkConfirmationInputPortProvider.createOkResponse()
        val publishCommand = FakePublishVkEventCommand()
        val usecase = ReceiveMessageUsecase(confirmationResponse, okResponse, publishCommand)
        val vkEvent = FakeVkProvider.createVkEvent(VkCallbackEvent.MESSAGE_NEW)
        val request = FakeVkConfirmationInputPortProvider.createRequest(vkEvent)

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals(okResponse.response.value, result.response.value)
        assertEquals(1, publishCommand.executeCalls.size)
        assertEquals(vkEvent, publishCommand.executeCalls.first().vkEvent)
    }
}
