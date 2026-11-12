package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserKnowledgeSummaryRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: ProcessUserKnowledgeSummaryInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val parsed = objectMapper.readValue(payload, UserKnowledgeSummaryRequestedPayload::class.java)
        inputPort.execute(ProcessUserKnowledgeSummaryInputPortRequest(parsed))
    }

    override fun event() = Event.USER_KNOWLEDGE_SUMMARY_REQUESTED
}
