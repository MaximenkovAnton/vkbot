package com.simarel.vkbot.share.domain.vo

@JvmInline
value class FromId(val value: Long) {
    companion object {
        fun of(value: Long) = FromId(value)
    }
    override fun toString(): String = value.toString()
}
