package com.simarel.vkbot.processor.adapter.input.event.messageReceived

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPort
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageReceivedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val messageReceivedInputPort: MessageReceivedInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        messageReceivedInputPort.execute(MessageReceivedInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_RECEIVED
}
