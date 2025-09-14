package com.simarel.vk.processor.adapter.input.event.messageNew

import com.fasterxml.jackson.databind.JsonNode
import com.simarel.vk.processor.adapter.input.event.messageNew.mapper.MessageMapper
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vk.share.adapter.input.EventProcessor
import com.simarel.vk.share.domain.Event
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageNewEventProcessor(
    val messageMapper: MessageMapper,
    val messageNewInputPort: MessageNewInputPort,
) : EventProcessor {

    override fun process(request: JsonNode) {
        val message = messageMapper.toDomain(request)
        messageNewInputPort.execute(MessageNewInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_RECEIVED
}