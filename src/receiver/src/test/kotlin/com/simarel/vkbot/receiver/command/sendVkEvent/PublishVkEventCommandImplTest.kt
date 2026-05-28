package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.port.output.PublishEventOutputPort
import com.simarel.vkbot.share.port.output.PublishEventOutputPortRequest
import com.simarel.vkbot.share.port.output.PublishEventOutputPortResponse
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class PublishVkEventCommandImplTest {

    class FakePublishEventOutputPort : PublishEventOutputPort {
        val executeCalls = ConcurrentLinkedQueue<PublishEventOutputPortRequest>()
        private val response = PublishEventOutputPortResponse()

        override fun execute(request: PublishEventOutputPortRequest): PublishEventOutputPortResponse {
            executeCalls.add(request)
            return response
        }
    }

    @Test
    fun `execute publishes MESSAGE_NEW event with message payload`() {
        // Given
        val outputPort = FakePublishEventOutputPort()
        val command = PublishVkEventCommandImpl(outputPort)
        val message = FakeVoProvider.createMessage()

        // When
        command.execute(PublishVkEventCommandRequest(message))

        // Then
        assertEquals(1, outputPort.executeCalls.size)
        val publishedRequest = outputPort.executeCalls.first()
        assertEquals(Event.MESSAGE_NEW, publishedRequest.event)
        assertEquals(message, (publishedRequest.payload.value as? Any))
    }
}
