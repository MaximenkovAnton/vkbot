package com.simarel.vkbot.ai.adapter.input.event.messageRequireAnswer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

class MessageRequireAnswerEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create().registerModule(JavaTimeModule())

    @Test
    fun `process reads message json and passes the parsed message to the input port`() {
        val date = FakeVoProvider.createDate(FIXED_DATE)
        val forwarded = FakeVoProvider.createMessage(
            date = date,
            messageText = FakeVoProvider.createMessageText("forwarded"),
        )
        val message = FakeVoProvider.createMessage(
            date = date,
            messageText = FakeVoProvider.createMessageText("current"),
            forwardedMessages = listOf(forwarded),
        )
        val inputPort = RecordingMessageRequireAnswerInputPort()
        val processor = MessageRequireAnswerEventProcessor(objectMapper, inputPort)

        processor.process(objectMapper.writeValueAsString(message))

        assertEquals(message, inputPort.requests.single().message)
    }

    @Test
    fun `event is MESSAGE_REQUIRE_ANSWER`() {
        val processor = MessageRequireAnswerEventProcessor(objectMapper, RecordingMessageRequireAnswerInputPort())

        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, processor.event())
    }

    private class RecordingMessageRequireAnswerInputPort : MessageRequireAnswerInputPort {
        val requests = ConcurrentLinkedQueue<MessageRequireAnswerInputPortRequest>()

        override fun execute(
            request: MessageRequireAnswerInputPortRequest,
        ): MessageRequireAnswerInputPortResponse {
            requests.add(request)
            return FakeAnswerProvider.createMessageRequireAnswerInputPortResponse()
        }
    }
}
