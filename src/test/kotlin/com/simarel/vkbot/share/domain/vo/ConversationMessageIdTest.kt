package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConversationMessageIdTest {

    @Test
    fun `of creates ConversationMessageId with valid value`() {
        // Given
        val value = 12345L

        // When
        val result = ConversationMessageId.of(value)

        // Then
        assertEquals(value, result.value)
        assertEquals(value.toString(), result.toString())
    }

    @Test
    fun `of throws ValidationException when value is null`() {
        // Given
        val value: Long? = null

        // When & Then
        val exception = assertThrows(ValidationException::class.java) {
            ConversationMessageId.of(value)
        }
        
        assertEquals("Conversation Message Id can't be null", exception.message)
    }
}