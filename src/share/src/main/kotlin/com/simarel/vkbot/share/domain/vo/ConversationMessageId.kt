package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.share.domain.exception.ValidationException

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

class ValidationConversationMessageIdNotNullException(message: String = "Conversation Message Id can't be null") :
    ValidationException(message)
