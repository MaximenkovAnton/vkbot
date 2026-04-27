package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.testfixtures.command.FakeIsRequireAnswerCommand
import com.simarel.vkbot.processor.testfixtures.port.input.FakeMessageNewInputPortProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageReceivedUsecaseInputTest {

    @Test
    fun `execute publishes MESSAGE_REQUIRE_ANSWER event when requireAnswer is true`() {
        // Given
        val publishCommand = FakePublishEventCommand()
        val isRequireAnswerCommand = FakeIsRequireAnswerCommand()
        isRequireAnswerCommand.shouldRequireAnswer = true
        val usecase = MessageReceivedUsecaseInput(publishCommand, isRequireAnswerCommand)
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

    @Test
    fun `execute does not publish event when requireAnswer is false`() {
        // Given
        val publishCommand = FakePublishEventCommand()
        val isRequireAnswerCommand = FakeIsRequireAnswerCommand()
        isRequireAnswerCommand.shouldRequireAnswer = false
        val usecase = MessageReceivedUsecaseInput(publishCommand, isRequireAnswerCommand)
        val request = FakeMessageNewInputPortProvider.createRequest()

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals("ok", result.value)
        assertEquals(0, publishCommand.executeCalls.size)
    }
}
