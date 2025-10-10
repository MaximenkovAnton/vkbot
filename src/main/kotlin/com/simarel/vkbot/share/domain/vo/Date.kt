package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException
import java.time.OffsetDateTime

@JvmInline
value class Date(val value: OffsetDateTime) {
    companion object {
        fun of(value: OffsetDateTime?): Date {
            if (value == null) throw ValidationDateNotNullException()
            return Date(value)
        }
    }
    override fun toString(): String = value.toString()
}

class ValidationDateNotNullException : ValidationException(message = "Date can't be null")
