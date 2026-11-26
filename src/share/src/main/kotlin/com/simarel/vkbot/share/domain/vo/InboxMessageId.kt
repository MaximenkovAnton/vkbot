package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.share.domain.exception.ValidationException

@JvmInline
value class InboxMessageId(val value: Long) {
    companion object {
        fun of(value: Long?): InboxMessageId {
            if (value == null) throw InboxMessageIdNullException()
            if (value <= 0) throw InboxMessageIdNotPositiveException(value)
            return InboxMessageId(value)
        }
    }

    override fun toString(): String = value.toString()
}

open class InboxMessageIdValidationException(message: String) : ValidationException(message)

class InboxMessageIdNullException : InboxMessageIdValidationException("Inbox Message Id can't be null")

class InboxMessageIdNotPositiveException(value: Long) :
    InboxMessageIdValidationException(
        "Inbox Message Id must be positive, was $value",
    )
