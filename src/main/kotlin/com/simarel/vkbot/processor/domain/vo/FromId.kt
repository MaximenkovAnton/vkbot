package com.simarel.vkbot.processor.domain.vo

@JvmInline
value class FromId(val value: Long) {
    companion object {
        fun of(value: Long) = FromId(value)
    }
    override fun toString(): String {
        return value.toString()
    }
}