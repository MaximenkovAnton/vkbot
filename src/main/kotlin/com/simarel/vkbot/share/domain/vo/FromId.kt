package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException

@JvmInline
value class FromId(val value: Long) {
    companion object {
        fun of(value: Long?): FromId {
            if (value == null) throw ValidationFromIdNotNullException()
            return FromId(value)
        }
        private const val GROUP_STARTING_ID = 2_000_000_000L
    }

    fun isHuman(): Boolean = value > 0 && value < GROUP_STARTING_ID
    fun isGroup(): Boolean = value < 0
    fun isGroupChat(): Boolean = value > GROUP_STARTING_ID

    override fun toString(): String = value.toString()
}

class ValidationFromIdNotNullException : ValidationException(message = "From Id can't be null")
