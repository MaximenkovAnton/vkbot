package com.simarel.vkbot.persistence.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPort
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageNewEventProcessor(
    private val objectMapper: ObjectMapper,
    private val saveMessageInputPort: SaveMessageInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        saveMessageInputPort.execute(SaveMessageInputPortRequest(message))
    }

    override fun event() = Event.MESSAGE_NEW
}
