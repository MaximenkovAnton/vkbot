package com.simarel.vkbot.vkFacade.domain

import com.simarel.vkbot.share.domain.exception.ValidationException
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import java.nio.ByteBuffer
import java.security.MessageDigest

@JvmInline
value class RandomId private constructor(val value: Int) {
    init {
        if (value == 0) throw RandomIdZeroValidationException()
    }

    companion object {
        private const val ALGORITHM = "SHA-256"
        private const val SMALLEST_RANDOM_ID = 1

        fun of(peerId: PeerId, messageText: MessageText): RandomId = of(hash(peerId, messageText))

        fun of(value: Int): RandomId = RandomId(if (value == 0) SMALLEST_RANDOM_ID else value)

        private fun hash(peerId: PeerId, messageText: MessageText): Int = ByteBuffer.wrap(
            MessageDigest.getInstance(ALGORITHM)
                .digest((messageText.value + peerId.value).toByteArray(Charsets.UTF_8)),
        ).getInt()
    }
}

open class RandomIdValidationException(message: String) : ValidationException(message)

class RandomIdZeroValidationException : RandomIdValidationException("RandomId must not be zero")
