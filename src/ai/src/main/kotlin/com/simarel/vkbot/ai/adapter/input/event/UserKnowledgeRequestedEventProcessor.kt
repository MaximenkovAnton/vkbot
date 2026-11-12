package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserKnowledgeRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: ProcessUserKnowledgeInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val parsed = objectMapper.readValue(payload, UserKnowledgeRequestedPayload::class.java)
        inputPort.execute(ProcessUserKnowledgeInputPortRequest(parsed))
    }

    override fun event() = Event.USER_KNOWLEDGE_REQUESTED
}
