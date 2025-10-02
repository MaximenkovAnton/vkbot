package com.simarel.vkbot.share.domain.vo

@JvmInline
value class PeerId(val value: Long) {
    companion object {
        fun of(value: Long) = PeerId(value)
    }

    override fun toString(): String = value.toString()
}
