package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortResponse
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val USER_ID = 7_600_001L
private const val PAYLOAD = """{"userId":7600001}"""

class UserKnowledgeSummaryRequestedEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @Test
    fun `parsesPayloadAndDelegatesToInputPort`() {
        val inputPort = RecordingProcessUserKnowledgeSummaryInputPort()

        UserKnowledgeSummaryRequestedEventProcessor(objectMapper, inputPort).process(PAYLOAD)

        assertEquals(USER_ID, inputPort.requests.single().payload.userId)
    }

    @Test
    fun `declaresUserKnowledgeSummaryRequestedEvent`() {
        val processor = UserKnowledgeSummaryRequestedEventProcessor(
            objectMapper,
            RecordingProcessUserKnowledgeSummaryInputPort(),
        )

        assertEquals(Event.USER_KNOWLEDGE_SUMMARY_REQUESTED, processor.event())
    }

    private class RecordingProcessUserKnowledgeSummaryInputPort : ProcessUserKnowledgeSummaryInputPort {
        val requests = ConcurrentLinkedQueue<ProcessUserKnowledgeSummaryInputPortRequest>()

        override fun execute(
            request: ProcessUserKnowledgeSummaryInputPortRequest,
        ): ProcessUserKnowledgeSummaryInputPortResponse {
            requests.add(request)
            return ProcessUserKnowledgeSummaryInputPortResponse
        }
    }
}
