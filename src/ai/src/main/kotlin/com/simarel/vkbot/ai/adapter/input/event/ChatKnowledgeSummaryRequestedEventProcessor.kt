package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPort
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ChatKnowledgeSummaryRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: ProcessChatKnowledgeSummaryInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val parsed = objectMapper.readValue(payload, ChatKnowledgeSummaryRequestedPayload::class.java)
        inputPort.execute(ProcessChatKnowledgeSummaryInputPortRequest(parsed))
    }

    override fun event() = Event.CHAT_KNOWLEDGE_SUMMARY_REQUESTED
}
