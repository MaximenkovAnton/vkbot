package com.simarel.vkbot.infrastructure.decorator.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.port.Port
import com.simarel.vkbot.share.port.PortRequest
import com.simarel.vkbot.share.port.PortResponse
import io.quarkus.logging.Log
import jakarta.decorator.Decorator
import jakarta.decorator.Delegate

@Decorator
open class UsecaseLoggingDecorator<REQ : PortRequest, RESP : PortResponse>(
    @Delegate val delegate: Port<REQ, RESP>,
    val objectMapper: ObjectMapper
) : Port<REQ, RESP> {
    override fun execute(request: REQ): RESP {
        if (Log.isTraceEnabled()) {
            Log.trace("Incoming usecase request: ${objectMapper.writeValueAsString(request)}")
        }

        val response = delegate.execute(request)

        if (Log.isTraceEnabled()) {
            Log.trace("Outcoming usecase response: ${objectMapper.writeValueAsString(response)}")
        }
        return response
    }
}
