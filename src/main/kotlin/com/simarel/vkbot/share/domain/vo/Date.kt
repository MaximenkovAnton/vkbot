package com.simarel.vkbot.share.domain.vo

import java.time.OffsetDateTime

@JvmInline
value class Date(val value: OffsetDateTime) {
    companion object {
        fun of(value: OffsetDateTime) = Date(value)
    }
    override fun toString(): String {
        return value.toString()
    }
}
