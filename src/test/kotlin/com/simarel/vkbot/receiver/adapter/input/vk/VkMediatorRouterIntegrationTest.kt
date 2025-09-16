package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.objectProvider.fake.domain.FakeVkProvider
import com.simarel.vkbot.objectProvider.fake.port.input.FakeReceiveMessageInputPort
import com.simarel.vkbot.objectProvider.fake.port.input.FakeVkConfirmationInputPortProvider
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VkMediatorRouterIntegrationTest {

    @Test
    fun `callback processes confirmation event successfully`() {
        // Given
        val confirmationResponse = FakeVkConfirmationInputPortProvider.createConfirmationResponse()
        val receiveMessageInputPort = FakeReceiveMessageInputPort(confirmationResponse)
        val router = VkMediatorRouterImpl(receiveMessageInputPort)
        val vkEvent = FakeVkProvider.createVkEvent(VkCallbackEvent.CONFIRMATION)

        // When
        val result = router.callback(vkEvent.value)

        // Then
        assertEquals("123456", result)
        assertEquals(1, receiveMessageInputPort.executeCalls.size)
        assertEquals(vkEvent, receiveMessageInputPort.executeCalls.first().vkEvent)
    }

    @Test
    fun `callback processes message_new event successfully`() {
        // Given
        val okResponse = FakeVkConfirmationInputPortProvider.createOkResponse()
        val receiveMessageInputPort = FakeReceiveMessageInputPort(okResponse)
        val router = VkMediatorRouterImpl(receiveMessageInputPort)
        val vkEvent = FakeVkProvider.createVkEvent(VkCallbackEvent.MESSAGE_NEW)

        // When
        val result = router.callback(vkEvent.value)

        // Then
        assertEquals("ok", result)
        assertEquals(1, receiveMessageInputPort.executeCalls.size)
        assertEquals(vkEvent, receiveMessageInputPort.executeCalls.first().vkEvent)
    }
}
