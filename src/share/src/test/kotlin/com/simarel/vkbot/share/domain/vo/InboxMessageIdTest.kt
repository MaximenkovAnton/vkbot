package com.simarel.vkbot.share.domain.vo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InboxMessageIdTest {

    @Test
    fun `of wraps a positive value`() {
        val result = InboxMessageId.of(42)

        assertEquals(42L, result.value)
        assertEquals("42", result.toString())
    }

    @Test
    fun `of throws when the value is null`() {
        val exception = assertThrows(InboxMessageIdNullException::class.java) {
            InboxMessageId.of(null)
        }

        assertEquals("Inbox Message Id can't be null", exception.message)
    }

    @Test
    fun `of throws when the value is not positive`() {
        listOf(
            0L to "Inbox Message Id must be positive, was 0",
            -5L to "Inbox Message Id must be positive, was -5",
        ).forEach { (value, expected) ->
            val exception = assertThrows(InboxMessageIdNotPositiveException::class.java) {
                InboxMessageId.of(value)
            }

            assertEquals(expected, exception.message)
        }
    }
}
