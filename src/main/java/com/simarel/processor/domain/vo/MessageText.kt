package com.simarel.processor.domain.vo

@JvmInline
value class MessageText(val value: String) {
    companion object {
        fun of(value: String) = MessageText(value)
    }
    override fun toString(): String {
        return value
    }
}