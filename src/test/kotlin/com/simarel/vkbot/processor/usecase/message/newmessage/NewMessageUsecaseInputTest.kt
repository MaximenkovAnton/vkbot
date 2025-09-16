package com.simarel.vkbot.processor.usecase.message.newmessage

import com.simarel.vkbot.objectProvider.fake.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.objectProvider.fake.port.input.FakeMessageNewInputPortProvider
import com.simarel.vkbot.share.domain.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NewMessageUsecaseInputTest {

    @Test
    fun `execute publishes MESSAGE_REQUIRE_ANSWER event when requireAnswer is true`() {
        // Given
        val publishCommand = FakePublishEventCommand()
        val usecase = NewMessageUsecaseInput(publishCommand)
        val request = FakeMessageNewInputPortProvider.createRequest()

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals("ok", result.value)
        assertEquals(1, publishCommand.executeCalls.size)
        val publishedEvent = publishCommand.executeCalls.first()
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, publishedEvent.event)
        assertEquals(request.message, publishedEvent.payload.value)
    }
}
