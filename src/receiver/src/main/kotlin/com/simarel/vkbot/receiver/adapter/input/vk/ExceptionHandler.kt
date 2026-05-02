package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.ValidationException
import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import io.quarkus.logging.Log
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

object ExceptionHandler {
    @ServerExceptionMapper
    fun handleInternalException(exception: VkBotAppException): Response {
        Log.error("Exception in route: ${exception.message}", exception)
        val status = when (exception.status()) {
            ExceptionStatus.VALIDATION_FAILED -> Response.Status.BAD_REQUEST
            ExceptionStatus.ACCESS_DENIED -> Response.Status.FORBIDDEN
            ExceptionStatus.EXTERNAL_FAILURE -> Response.Status.SERVICE_UNAVAILABLE
            ExceptionStatus.UNKNOWN -> Response.Status.SERVICE_UNAVAILABLE
        }
        return Response.status(status)
            .entity("{\"error\":\"${exception.message}\"}")
            .type("application/json")
            .build()
    }

    @ServerExceptionMapper
    fun handleClassCastException(exception: ClassCastException): Response =
        handleInternalException(
            ValidationException(exception.message ?: "Unknown class cast exception", exception),
        )

    @ServerExceptionMapper
    fun handleUnknownException(exception: RuntimeException): Response {
        Log.fatal("Unhandled exception", exception)
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity("{\"error\":\"${exception.message}\"}")
            .type("application/json")
            .build()
    }
}
