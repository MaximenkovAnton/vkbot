package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException

@JvmInline
value class ConversationMessageId(val value: Long) {
    companion object {
        fun of(value: Long?): ConversationMessageId {
            if (value == null) throw ValidationConversationMessageIdNotNullException()
            return ConversationMessageId(value)
        }
    }
    override fun toString(): String = value.toString()
}

class ValidationConversationMessageIdNotNullException : ValidationException(message = "Conversation Message Id can't be null")
