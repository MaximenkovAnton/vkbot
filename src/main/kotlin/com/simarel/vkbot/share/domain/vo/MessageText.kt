package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException

@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String?): MessageText {
            if (value == null) throw ValidationMessageTextNotNullException()
            return MessageText(value)
        }
    }
    fun startsWith(prefix: String): Boolean = value.startsWith(prefix)
    fun contains(text: String): Boolean = value.contains(text, ignoreCase = true)
    override fun toString(): String = value
}

class ValidationMessageTextNotNullException : ValidationException(message = "Message text can't be null")
