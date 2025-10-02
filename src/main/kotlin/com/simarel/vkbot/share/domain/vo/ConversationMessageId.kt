package com.simarel.vkbot.share.domain.vo

@JvmInline
value class ConversationMessageId(val value: Long) {
    companion object {
        fun of(value: Long) = ConversationMessageId(value)
    }
    override fun toString(): String {
        return value.toString()
    }
}
