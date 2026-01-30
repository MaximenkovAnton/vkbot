package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageTextTest {

    @Test
    fun `of creates MessageText with valid value`() {
        // Given
        val value = "Hello, world!"

        // When
        val result = MessageText.of(value)

        // Then
        assertEquals(value, result.value)
        assertEquals(value, result.toString())
    }

    @Test
    fun `of throws ValidationException when value is null`() {
        // Given
        val value: String? = null

        // When & Then
        val exception = assertThrows(ValidationException::class.java) {
            MessageText.of(value)
        }

        assertEquals("Message text can't be null", exception.message)
    }

    @Test
    fun `startsWith returns true when message starts with prefix`() {
        // Given
        val messageText = MessageText.of("Hello, world!")

        // When
        val result = messageText.startsWith("Hello")

        // Then
        assertTrue(result)
    }

    @Test
    fun `startsWith returns false when message does not start with prefix`() {
        // Given
        val messageText = MessageText.of("Hello, world!")

        // When
        val result = messageText.startsWith("World")

        // Then
        assertFalse(result)
    }

    @Test
    fun `contains returns true when message contains text`() {
        // Given
        val messageText = MessageText.of("Hello, world!")

        // When
        val result = messageText.contains("world")

        // Then
        assertTrue(result)
    }

    @Test
    fun `contains returns false when message does not contain text`() {
        // Given
        val messageText = MessageText.of("Hello, world!")

        // When
        val result = messageText.contains("universe")

        // Then
        assertFalse(result)
    }
}
