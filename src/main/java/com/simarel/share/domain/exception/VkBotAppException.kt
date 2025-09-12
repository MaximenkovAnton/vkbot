package com.simarel.share.domain.exception

import com.simarel.receiver.domain.exception.ExceptionStatus

abstract class VkBotAppException(message: String = "Unhandled Exception on Vk Bot App") : RuntimeException(message) {
    abstract fun status(): ExceptionStatus
}