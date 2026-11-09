package com.simarel.vkbot.share.domain.exception

abstract class VkBotAppException(
    message: String = "Unhandled Exception on Vk Bot App",
    exception: Throwable? = null,
) : RuntimeException(
    message,
    exception,
) {
    abstract fun status(): ExceptionStatus
}
