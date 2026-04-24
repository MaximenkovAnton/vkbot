package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.share.domain.exception.ValidationException

@JvmInline
value class PeerId(val value: Long) {
    companion object {
        fun of(value: Long?): PeerId {
            if (value == null) throw ValidationPeerIdNotNullException()
            return PeerId(value)
        }
        private const val GROUP_STARTING_ID = 2_000_000_000L
    }

    fun isHuman(): Boolean = value in 1..GROUP_STARTING_ID
    fun isGroup(): Boolean = value < 0
    fun isGroupChat(): Boolean = value > GROUP_STARTING_ID

    override fun toString(): String = value.toString()
}

class ValidationPeerIdNotNullException : ValidationException(message = "Peer Id can't be null")
