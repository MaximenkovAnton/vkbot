package com.simarel.vk.processor.adapter.input.event.messageNew

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vk.share.adapter.input.EventProcessor
import com.simarel.vk.share.domain.Event
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageNewEventProcessor(
    val objectMapper: ObjectMapper,
    val messageNewInputPort: MessageNewInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        messageNewInputPort.execute(MessageNewInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_RECEIVED
}