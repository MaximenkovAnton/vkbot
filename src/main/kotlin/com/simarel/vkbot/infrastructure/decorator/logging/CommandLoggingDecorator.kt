package com.simarel.vkbot.infrastructure.decorator.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import io.quarkus.logging.Log
import jakarta.decorator.Decorator
import jakarta.decorator.Delegate

@Decorator
open class CommandLoggingDecorator<REQ : CommandRequest, RESP : CommandResponse>(
    @Delegate val delegate: Command<REQ, RESP>,
    val objectMapper: ObjectMapper,
) : Command<REQ, RESP> {
    override fun execute(request: REQ): RESP {
        if (Log.isTraceEnabled()) {
            Log.trace("Incoming command request: ${objectMapper.writeValueAsString(request)}")
        }

        val response = delegate.execute(request)

        if (Log.isTraceEnabled()) {
            Log.trace("Outcoming command response: ${objectMapper.writeValueAsString(response)}")
        }
        return response
    }
}
