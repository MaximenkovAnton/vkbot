package com.simarel.vkbot.share.domain.vo

import com.simarel.vkbot.receiver.domain.exception.ValidationException

@JvmInline
value class PeerId(val value: Long) {
    companion object {
        fun of(value: Long?): PeerId {
            if (value == null) throw ValidationPeerIdNotNullException()
            return PeerId(value)
        }
    }

    override fun toString(): String = value.toString()
}

class ValidationPeerIdNotNullException : ValidationException(message = "Peer Id can't be null")
