package com.simarel.vkbot.processor.adapter.input.event.messageNew

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vkbot.share.adapter.input.EventProcessor
import com.simarel.vkbot.share.domain.Event
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