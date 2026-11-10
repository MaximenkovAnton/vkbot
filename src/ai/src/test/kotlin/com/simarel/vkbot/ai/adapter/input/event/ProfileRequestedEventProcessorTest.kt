package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPort
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortRequest
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-11-10T12:00:00Z")

class ProfileRequestedEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create().registerModule(JavaTimeModule())

    @Test
    fun `parses the message payload and calls the input port`() {
        val message = FakeVoProvider.createMessage(
            date = FakeVoProvider.createDate(FIXED_DATE),
            fromId = FakeVoProvider.createFromId(7_600_001L),
            groupId = FakeVoProvider.createGroupId(232_142_875L),
            peerId = FakeVoProvider.createPeerId(2_000_000_001L),
            conversationMessageId = FakeVoProvider.createConversationMessageId(500L),
            messageText = FakeVoProvider.createMessageText("расскажи о Паше Селезневе"),
        )
        val inputPort = RecordingProcessProfileInputPort()
        val processor = ProfileRequestedEventProcessor(objectMapper, inputPort)

        processor.process(objectMapper.writeValueAsString(message))

        assertEquals(1, inputPort.requests.size)
        assertEquals(message, inputPort.requests.single().message)
    }

    @Test
    fun `declares the PROFILE_REQUESTED event`() {
        val processor = ProfileRequestedEventProcessor(objectMapper, RecordingProcessProfileInputPort())

        assertEquals(Event.PROFILE_REQUESTED, processor.event())
    }

    private class RecordingProcessProfileInputPort : ProcessProfileInputPort {
        val requests = ConcurrentLinkedQueue<ProcessProfileInputPortRequest>()

        override fun execute(
            request: ProcessProfileInputPortRequest,
        ): ProcessProfileInputPortResponse {
            requests.add(request)
            return FakeProfileProvider.createProcessProfileInputPortResponse()
        }
    }
}
