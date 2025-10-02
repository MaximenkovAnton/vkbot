package com.simarel.vkbot.share.domain.vo

@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String) = MessageText(value)
    }
    override fun toString(): String = value
}
