package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeKnowledgeProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider
import com.simarel.vkbot.testfixtures.objectProvider.ObjectMapperProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class UserKnowledgeRequestedEventProcessorTest {

    private val objectMapper: ObjectMapper = ObjectMapperProvider.create()

    @Test
    fun `parsesPayloadAndDelegatesToInputPort`() {
        val payload = FakeEventProvider.createUserKnowledgeRequestedPayload(
            peerId = 2_000_000_001,
            upToConversationMessageId = 101,
        )
        val inputPort = RecordingProcessUserKnowledgeInputPort()

        UserKnowledgeRequestedEventProcessor(objectMapper, inputPort)
            .process(objectMapper.writeValueAsString(payload))

        assertEquals(
            listOf(FakeKnowledgeProvider.createProcessUserKnowledgeInputPortRequest(payload = payload)),
            inputPort.requests.toList(),
        )
    }

    @Test
    fun `declaresUserKnowledgeRequestedEvent`() {
        val processor = UserKnowledgeRequestedEventProcessor(
            objectMapper,
            RecordingProcessUserKnowledgeInputPort(),
        )

        assertEquals(Event.USER_KNOWLEDGE_REQUESTED, processor.event())
    }

    private class RecordingProcessUserKnowledgeInputPort : ProcessUserKnowledgeInputPort {
        val requests = ConcurrentLinkedQueue<ProcessUserKnowledgeInputPortRequest>()

        override fun execute(
            request: ProcessUserKnowledgeInputPortRequest,
        ): ProcessUserKnowledgeInputPortResponse {
            requests.add(request)
            return ProcessUserKnowledgeInputPortResponse
        }
    }
}
