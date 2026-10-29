package com.simarel.vkbot.persistence.adapter.output.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.port.output.ForwardedMessagesSerializationPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class JacksonForwardedMessagesAdapter(
    private val objectMapper: ObjectMapper,
) : ForwardedMessagesSerializationPort {
    override fun execute(
        request: ForwardedMessagesSerializationPort.Request,
    ): ForwardedMessagesSerializationPort.Response {
        val json = if (request.messages.isEmpty()) {
            null
        } else {
            objectMapper.writeValueAsString(request.messages)
        }
        return ForwardedMessagesSerializationPort.Response(json)
    }
}
