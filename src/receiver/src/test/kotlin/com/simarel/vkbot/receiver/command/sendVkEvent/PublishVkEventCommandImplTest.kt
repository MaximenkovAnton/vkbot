package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.receiver.fixtures.FakePublishVkEventCommandProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.adapter.output.mq.FakePublishEventOutputPort
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublishVkEventCommandImplTest {

    @Test
    fun `execute publishes MESSAGE_NEW event with message payload`() {
        // Given
        val outputPort = FakePublishEventOutputPort()
        val command = PublishVkEventCommandImpl(outputPort)
        val message = FakeVoProvider.createMessage()

        // When
        command.execute(FakePublishVkEventCommandProvider.createRequest(message))

        // Then
        assertEquals(1, outputPort.executeCalls.size)
        val publishedRequest = outputPort.executeCalls.first()
        assertEquals(Event.MESSAGE_NEW, publishedRequest.event)
        assertEquals(message, (publishedRequest.payload.value as? Any))
    }
}
