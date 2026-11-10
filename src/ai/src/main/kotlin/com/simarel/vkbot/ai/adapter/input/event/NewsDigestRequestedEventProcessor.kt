package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPort
import com.simarel.vkbot.ai.port.input.news.ProcessNewsDigestInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class NewsDigestRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: ProcessNewsDigestInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val message = objectMapper.readValue(payload, Message::class.java)
        inputPort.execute(ProcessNewsDigestInputPortRequest(message))
    }

    override fun event() = Event.NEWS_DIGEST_REQUESTED
}
