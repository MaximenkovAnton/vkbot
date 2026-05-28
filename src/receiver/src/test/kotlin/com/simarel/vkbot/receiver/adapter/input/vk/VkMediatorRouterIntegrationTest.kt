package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.fixtures.FakeMessageMapper
import com.simarel.vkbot.receiver.fixtures.FakeVkConfirmationInputPortProvider
import com.simarel.vkbot.receiver.fixtures.FakeVkProvider
import com.simarel.vkbot.receiver.fixtures.port.FakeReceiveMessageInputPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import jakarta.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VkMediatorRouterIntegrationTest {

    @Test
    fun `callback processes confirmation event successfully`() {
        // Given
        val confirmationResponse = FakeVkConfirmationInputPortProvider.createConfirmationResponse()
        val receiveMessageInputPort = FakeReceiveMessageInputPort(confirmationResponse)
        val messageMapper = FakeMessageMapper()
        val router = VkMediatorRouterImpl(receiveMessageInputPort, messageMapper)
        val jsonEvent = Json.createObjectBuilder()
            .add("type", "confirmation")
            .add("group_id", FakeVoProvider.createGroupId().value)
            .add("secret", FakeVkProvider.SECRET)
            .build()

        // When
        val result = router.callback(jsonEvent)

        // Then
        assertEquals("123456", result)
        assertEquals(1, receiveMessageInputPort.executeCalls.size)
        assertEquals(VkCallbackEvent.CONFIRMATION, receiveMessageInputPort.executeCalls.first().vkEvent.type)
        assertEquals(0, messageMapper.toDomainCalls.size)
    }

    @Test
    fun `callback processes message_new event successfully`() {
        // Given
        val okResponse = FakeVkConfirmationInputPortProvider.createOkResponse()
        val receiveMessageInputPort = FakeReceiveMessageInputPort(okResponse)
        val messageMapper = FakeMessageMapper()
        val router = VkMediatorRouterImpl(receiveMessageInputPort, messageMapper)
        val jsonEvent = Json.createObjectBuilder()
            .add("type", "message_new")
            .add("group_id", FakeVoProvider.createGroupId().value)
            .add(
                "object",
                Json.createObjectBuilder()
                    .add(
                        "message",
                        Json.createObjectBuilder()
                            .add("text", FakeVoProvider.createMessageText().value)
                            .add("from_id", FakeVoProvider.createHumanFromId().value)
                            .add("peer_id", FakeVoProvider.createPeerId().value)
                            .add("conversation_message_id", FakeVoProvider.createConversationMessageId().value)
                            .add("date", FakeVoProvider.createDate().value.toEpochSecond())
                    )
            )
            .add("secret", FakeVkProvider.SECRET)
            .build()

        // When
        val result = router.callback(jsonEvent)

        // Then
        assertEquals("ok", result)
        assertEquals(1, receiveMessageInputPort.executeCalls.size)
        assertEquals(VkCallbackEvent.MESSAGE_NEW, receiveMessageInputPort.executeCalls.first().vkEvent.type)
        assertEquals(1, messageMapper.toDomainCalls.size)
    }
}
