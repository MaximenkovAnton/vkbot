package com.simarel.vkbot.processor.adapter.input.event.messageRequireAnswer

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.objectProvider.fake.domain.FakeMessageProvider
import com.simarel.vkbot.objectProvider.fake.port.input.FakeMessageRequireAnswerInputPort
import com.simarel.vkbot.share.domain.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageRequireAnswerProcessorTest {

    private val objectMapper = ObjectMapper()
    private val fakeInputPort = FakeMessageRequireAnswerInputPort()
    private val processor = MessageRequireAnswerProcessor(objectMapper, fakeInputPort)

    @Test
    fun `process should parse json and delegate to input port`() {
        val message = FakeMessageProvider.createMessage()
        val json = objectMapper.writeValueAsString(message)

        processor.process(json)

        assertEquals(1, fakeInputPort.executeCalls.size)
        val request = fakeInputPort.executeCalls.element()
        assertEquals(message, request.message)
    }

    @Test
    fun `event should return MESSAGE_REQUIRE_ANSWER`() {
        assertEquals(Event.MESSAGE_REQUIRE_ANSWER, processor.event())
    }
}
