package com.simarel.adapter.input.vk

import com.simarel.domain.exception.ExceptionStatus
import com.simarel.domain.exception.VkBotAppException
import io.quarkus.logging.Log
import io.quarkus.vertx.web.Route
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import org.apache.http.HttpStatus

open class ExceptionHandler {
    @Route(path = "/*", type = Route.HandlerType.FAILURE, produces = ["application/json"])
    fun handleReactiveRouteException(rc: RoutingContext) {
        val failure: Throwable = rc.failure()
        val response: HttpServerResponse = rc.response()

        Log.error("Exception in reactive route: ${failure.message}", failure)

        val status = if(failure is VkBotAppException) {
            failure.status()
        } else {
            ExceptionStatus.UNKNOWN
        }

        response.setStatusCode(
            when(status) {
                ExceptionStatus.VALIDATION_FAILED -> HttpStatus.SC_BAD_REQUEST
                ExceptionStatus.ACCESS_DENIED -> HttpStatus.SC_FORBIDDEN
                ExceptionStatus.UNKNOWN -> HttpStatus.SC_SERVICE_UNAVAILABLE
            }
        ).end("{\"error\": \"${failure.message}\"}")
    }
}