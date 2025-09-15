package com.simarel.vk.receiver.adapter.input.vk

import com.simarel.vk.receiver.domain.exception.ExceptionStatus
import com.simarel.vk.share.domain.exception.VkBotAppException
import io.quarkus.logging.Log
import jakarta.enterprise.context.Dependent
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@Dependent
open class ExceptionHandler {
    @ServerExceptionMapper
    fun handleReactiveRouteException(exception: VkBotAppException): RestResponse<String> {
        Log.error("Exception in route: ${exception.message}", exception)

        return RestResponse.status(
            when(exception.status()) {
                ExceptionStatus.VALIDATION_FAILED -> Response.Status.BAD_REQUEST
                ExceptionStatus.ACCESS_DENIED -> Response.Status.FORBIDDEN
                ExceptionStatus.UNKNOWN -> Response.Status.SERVICE_UNAVAILABLE
            },
        "{\"error\": \"${exception.message}\"}")
    }
}