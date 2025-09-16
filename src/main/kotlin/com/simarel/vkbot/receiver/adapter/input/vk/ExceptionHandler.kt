package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import io.quarkus.logging.Log
import jakarta.enterprise.context.Dependent
import jakarta.json.Json
import jakarta.json.JsonObject
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@Dependent
open class ExceptionHandler {
    @ServerExceptionMapper
    fun handleReactiveRouteException(exception: VkBotAppException): RestResponse<JsonObject> {
        Log.error("Exception in route: ${exception.message}", exception)

        val response = RestResponse.status(
            when(exception.status()) {
                ExceptionStatus.VALIDATION_FAILED -> Response.Status.BAD_REQUEST
                ExceptionStatus.ACCESS_DENIED -> Response.Status.FORBIDDEN
                ExceptionStatus.EXTERNAL_FAILURE -> Response.Status.SERVICE_UNAVAILABLE
                ExceptionStatus.UNKNOWN -> Response.Status.SERVICE_UNAVAILABLE
            },
            Json.createObjectBuilder().add("error", exception.message).build()
        )
        response.metadata["content-type"] = listOf("application/json")
        return response
    }
}