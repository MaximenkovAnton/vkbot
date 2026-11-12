package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class ChatKnowledgeSummaryRequestedEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @Test
    fun `parsesPayloadAndDelegatesToInputPort`() {
        val payload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(peerId = 2_000_000_001)
        val inputPort = RecordingProcessChatKnowledgeSummaryInputPort()

        ChatKnowledgeSummaryRequestedEventProcessor(objectMapper, inputPort)
            .process(objectMapper.writeValueAsString(payload))

        assertEquals(
            listOf(FakeKnowledgeProvider.createProcessChatKnowledgeSummaryInputPortRequest(payload = payload)),
            inputPort.requests.toList(),
        )
    }

    @Test
    fun `declaresChatKnowledgeSummaryRequestedEvent`() {
        val processor = ChatKnowledgeSummaryRequestedEventProcessor(
            objectMapper,
            RecordingProcessChatKnowledgeSummaryInputPort(),
        )

        assertEquals(Event.CHAT_KNOWLEDGE_SUMMARY_REQUESTED, processor.event())
    }

    private class RecordingProcessChatKnowledgeSummaryInputPort : ProcessChatKnowledgeSummaryInputPort {
        val requests = ConcurrentLinkedQueue<ProcessChatKnowledgeSummaryInputPortRequest>()

        override fun execute(
            request: ProcessChatKnowledgeSummaryInputPortRequest,
        ): ProcessChatKnowledgeSummaryInputPortResponse {
            requests.add(request)
            return ProcessChatKnowledgeSummaryInputPortResponse
        }
    }
}
