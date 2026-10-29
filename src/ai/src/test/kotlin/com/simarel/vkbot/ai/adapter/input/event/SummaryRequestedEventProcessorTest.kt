package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val PEER_ID = 2_000_000_001L
private const val CONVERSATION_MESSAGE_ID = 7_777L

class SummaryRequestedEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @Test
    fun `process reads the payload json and passes the identifiers to the input port`() {
        val inputPort = RecordingProcessSummaryInputPort()
        val payload = FakeEventProvider.createSummaryRequestedPayload(
            peerId = PEER_ID,
            conversationMessageId = CONVERSATION_MESSAGE_ID,
        )

        SummaryRequestedEventProcessor(objectMapper, inputPort)
            .process(objectMapper.writeValueAsString(payload))

        assertEquals(
            FakeSummaryProvider.createProcessSummaryInputPortRequest(
                peerId = PEER_ID,
                conversationMessageId = CONVERSATION_MESSAGE_ID,
            ),
            inputPort.requests.single(),
        )
    }

    @Test
    fun `event is SUMMARY_REQUESTED`() {
        val processor = SummaryRequestedEventProcessor(objectMapper, RecordingProcessSummaryInputPort())

        assertEquals(Event.SUMMARY_REQUESTED, processor.event())
    }

    private class RecordingProcessSummaryInputPort : ProcessSummaryInputPort {
        val requests = ConcurrentLinkedQueue<ProcessSummaryInputPortRequest>()

        override fun execute(request: ProcessSummaryInputPortRequest): ProcessSummaryInputPortResponse {
            requests.add(request)
            return ProcessSummaryInputPortResponse
        }
    }
}
