package com.simarel.vkbot.ai.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPort
import com.simarel.vkbot.ai.port.input.profile.ProcessProfileInputPortRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.port.input.EventProcessor
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProfileRequestedEventProcessor(
    private val objectMapper: ObjectMapper,
    private val inputPort: ProcessProfileInputPort,
) : EventProcessor {

    override fun process(payload: String) {
        val message = objectMapper.readValue(payload, Message::class.java)
        inputPort.execute(ProcessProfileInputPortRequest(message))
    }

    override fun event() = Event.PROFILE_REQUESTED
}
