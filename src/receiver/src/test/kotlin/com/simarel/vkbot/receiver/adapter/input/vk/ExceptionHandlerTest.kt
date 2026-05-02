package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.ValidationException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionHandlerTest {

    @Test
    fun `handleInternalException returns BAD_REQUEST for validation exceptions`() {
        // Given
        val exception = ValidationException("Validation failed")

        // When
        val result = ExceptionHandler.handleInternalException(exception)

        // Then
        assertEquals(Response.Status.BAD_REQUEST.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }

    @Test
    fun `handleClassCastException delegates to handleInternalException`() {
        // Given
        val exception = ClassCastException("Invalid cast")

        // When
        val result = ExceptionHandler.handleClassCastException(exception)

        // Then
        assertEquals(Response.Status.BAD_REQUEST.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }

    @Test
    fun `handleUnknownException returns SERVICE_UNAVAILABLE`() {
        // Given
        val exception = RuntimeException("Unexpected error")

        // When
        val result = ExceptionHandler.handleUnknownException(exception)

        // Then
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }
}
