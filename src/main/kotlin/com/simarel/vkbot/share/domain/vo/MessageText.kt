package com.simarel.vkbot.share.domain.vo

@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String) = MessageText(value)
    }
    fun startsWith(prefix: String): Boolean = value.startsWith(prefix)
    fun contains(text: String): Boolean = value.contains(text, ignoreCase = true)
    override fun toString(): String = value
}
