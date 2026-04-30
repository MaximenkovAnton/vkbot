package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.share.domain.exception.ValidationException

@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String?): MessageText {
            return MessageText(value ?: "")
        }
    }

    fun startsWith(prefix: String): Boolean = value.startsWith(prefix)
    fun contains(text: String): Boolean = value.contains(text, ignoreCase = true)
    override fun toString(): String = value
}
