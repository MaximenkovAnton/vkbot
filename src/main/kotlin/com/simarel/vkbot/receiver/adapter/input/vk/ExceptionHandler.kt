package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.ExceptionStatus
import com.simarel.vkbot.receiver.domain.exception.ValidationException
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import io.quarkus.logging.Log
import jakarta.enterprise.context.Dependent
import jakarta.json.Json
import jakarta.json.JsonObject
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@Dependent
open class ExceptionHandler {
    @ServerExceptionMapper
    fun handleInternalException(exception: VkBotAppException): RestResponse<JsonObject> {
        Log.error("Exception in route: ${exception.message}", exception)
        val status = when (exception.status()) {
            ExceptionStatus.VALIDATION_FAILED -> Response.Status.BAD_REQUEST
            ExceptionStatus.ACCESS_DENIED -> Response.Status.FORBIDDEN
            ExceptionStatus.EXTERNAL_FAILURE -> Response.Status.SERVICE_UNAVAILABLE
            ExceptionStatus.UNKNOWN -> Response.Status.SERVICE_UNAVAILABLE
        }
        return createResponse(status, Json.createObjectBuilder().add("error", exception.message).build())
    }

    @ServerExceptionMapper
    fun handleClassCastException(exception: ClassCastException): RestResponse<JsonObject> = handleInternalException(ValidationException(exception.message ?: "Unknown class cast exception", exception))

    @ServerExceptionMapper
    fun handleUnknownException(exception: RuntimeException): RestResponse<JsonObject> {
        Log.fatal("Unhandled exception", exception)
        return createResponse(
            Response.Status.SERVICE_UNAVAILABLE,
            Json.createObjectBuilder().add("error", exception.message).build(),
        )
    }

    private fun createResponse(status: Response.Status, body: JsonObject): RestResponse<JsonObject> {
        val response = RestResponse.status(status, body)
        response.metadata["content-type"] = listOf("application/json")
        return response
    }
}
