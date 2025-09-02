package com.simarel.domain.vo

@JvmInline
value class PeerId(val value: Long) {
    companion object {
        fun of(value: Long) = PeerId(value)
    }

    override fun toString(): String {
        return value.toString()
    }
}
