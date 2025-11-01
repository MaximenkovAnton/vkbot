package com.simarel.vkbot.share.domain.exception

import com.simarel.vkbot.receiver.domain.exception.ExceptionStatus

abstract class VkBotAppException(
    message: String = "Unhandled Exception on Vk Bot App",
    exception: RuntimeException? = null,
) : RuntimeException(
    message,
    exception,
) {
    abstract fun status(): ExceptionStatus
}
