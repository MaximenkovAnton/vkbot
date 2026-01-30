package com.simarel.vkbot.receiver.adapter.input.vk

import com.simarel.vkbot.receiver.domain.exception.ExceptionStatus
import com.simarel.vkbot.receiver.domain.exception.ValidationException
import com.simarel.vkbot.share.domain.exception.VkBotAppException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionHandlerTest {

    @Test
    fun `handleInternalException returns BAD_REQUEST for validation exceptions`() {
        // Given
        val handler = ExceptionHandler()
        val exception = ValidationException("Validation failed")

        // When
        val result = handler.handleInternalException(exception)

        // Then
        assertEquals(Response.Status.BAD_REQUEST.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }

    @Test
    fun `handleClassCastException delegates to handleInternalException`() {
        // Given
        val handler = ExceptionHandler()
        val exception = ClassCastException("Invalid cast")

        // When
        val result = handler.handleClassCastException(exception)

        // Then
        assertEquals(Response.Status.BAD_REQUEST.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }

    @Test
    fun `handleUnknownException returns SERVICE_UNAVAILABLE`() {
        // Given
        val handler = ExceptionHandler()
        val exception = RuntimeException("Unexpected error")

        // When
        val result = handler.handleUnknownException(exception)

        // Then
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.statusCode, result.status)
        assertEquals("application/json", result.metadata["content-type"]?.firstOrNull())
    }

    // Test implementation of VkBotAppException for testing different status codes
    class TestException(
        private val exceptionStatus: ExceptionStatus,
        message: String,
    ) : VkBotAppException(message) {
        override fun status(): ExceptionStatus = exceptionStatus
    }
}
