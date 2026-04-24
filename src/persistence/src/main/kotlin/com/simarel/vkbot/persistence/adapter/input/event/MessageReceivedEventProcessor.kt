package com.simarel.vkbot.persistence.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.usecase.SaveMessageUsecase
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageReceivedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val saveMessageUsecase: SaveMessageUsecase,
) : EventProcessor {

    override fun process(jsonString: String) {
        val message = objectMapper.readValue(jsonString, Message::class.java)
        saveMessageUsecase.execute(message)
    }

    override fun event() = Event.MESSAGE_RECEIVED
}
