package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPort
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SummaryRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val processSummaryInputPort: ProcessSummaryInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val summaryPayload = objectMapper.readValue(payload, SummaryRequestedPayload::class.java)
        processSummaryInputPort.execute(
            ProcessSummaryInputPortRequest(
                peerId = summaryPayload.peerId,
                conversationMessageId = summaryPayload.conversationMessageId
            )
        )
    }

    override fun event() = Event.SUMMARY_REQUESTED

    data class SummaryRequestedPayload(
        val peerId: Long,
        val conversationMessageId: Long
    )
}
