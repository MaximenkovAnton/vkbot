package com.simarel.vkbot.processor.usecase.message.requireAnswer

import com.simarel.vkbot.objectProvider.fake.command.processor.FakeMessageAnswerTextGenerateCommand
import com.simarel.vkbot.objectProvider.fake.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.objectProvider.fake.port.input.FakeMessageRequireAnswerInputPortProvider
import com.simarel.vkbot.share.domain.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageRequireAnswerUsecaseInputTest {

    @Test
    fun `execute generates answer and publishes SEND_MESSAGE event`() {
        // Given
        val messageAnswerCommand = FakeMessageAnswerTextGenerateCommand()
        val publishCommand = FakePublishEventCommand()
        val usecase = MessageRequireAnswerUsecaseInput(messageAnswerCommand, publishCommand)
        val request = FakeMessageRequireAnswerInputPortProvider.createRequest()

        // When
        val result = usecase.execute(request)

        // Then
        assertEquals("ok", result.value)

        // Verify AI command was called
        assertEquals(1, messageAnswerCommand.executeCalls.size)
        assertEquals(request.message, messageAnswerCommand.executeCalls.first().message)

        // Verify event was published
        assertEquals(1, publishCommand.executeCalls.size)
        val publishedEvent = publishCommand.executeCalls.first()
        assertEquals(Event.SEND_MESSAGE, publishedEvent.event)
        // Answer message should have the same properties as original, but with AI-generated text
        val answerMessage = publishedEvent.payload.value as com.simarel.vkbot.share.domain.model.Message
        assertEquals(request.message.peerId, answerMessage.peerId)
        assertEquals(request.message.fromId, answerMessage.fromId)
        assertEquals(request.message.groupId, answerMessage.groupId)
        assertEquals(request.message.conversationMessageId, answerMessage.conversationMessageId)
        assertEquals(messageAnswerCommand.executeCalls.first().message, request.message)
    }
}
