package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPort
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortRequest
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeNewsDigestProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

class NewsDigestRequestedEventProcessorTest {

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
            messageText = FakeVoProvider.createMessageText("что нового"),
            forwardedMessages = listOf(forwarded),
        )
        val inputPort = RecordingProcessNewsDigestInputPort()
        val processor = NewsDigestRequestedEventProcessor(objectMapper, inputPort)

        processor.process(objectMapper.writeValueAsString(message))

        assertEquals(message, inputPort.requests.single().message)
    }

    @Test
    fun `event is NEWS_DIGEST_REQUESTED`() {
        val processor = NewsDigestRequestedEventProcessor(objectMapper, RecordingProcessNewsDigestInputPort())

        assertEquals(Event.NEWS_DIGEST_REQUESTED, processor.event())
    }

    private class RecordingProcessNewsDigestInputPort : ProcessNewsDigestInputPort {
        val requests = ConcurrentLinkedQueue<ProcessNewsDigestInputPortRequest>()

        override fun execute(
            request: ProcessNewsDigestInputPortRequest,
        ): ProcessNewsDigestInputPortResponse {
            requests.add(request)
            return FakeNewsDigestProvider.createProcessNewsDigestInputPortResponse()
        }
    }
}
