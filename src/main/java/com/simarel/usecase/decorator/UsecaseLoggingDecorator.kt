package com.simarel.usecase.decorator

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.port.Port
import com.simarel.port.PortRequest
import com.simarel.port.PortResponse
import io.quarkus.logging.Log
import jakarta.decorator.Decorator
import jakarta.decorator.Delegate

@Decorator
open class UsecaseLoggingDecorator<REQ: PortRequest, RESP: PortResponse>(
    @Delegate val delegate: Port<REQ, RESP>,
    val objectMapper: ObjectMapper
): Port<REQ, RESP> {
    override fun execute(request: REQ): RESP {
        Log.debug("Incoming request: ${objectMapper.writeValueAsString(request)}")
        val response = delegate.execute(request)
        Log.debug("Outcoming response: ${objectMapper.writeValueAsString(response)}")
        return response
    }
}