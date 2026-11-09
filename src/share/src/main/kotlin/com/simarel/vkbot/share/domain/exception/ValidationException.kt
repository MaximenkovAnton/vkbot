package com.simarel.vkbot.share.domain.exception

open class ValidationException(message: String) : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.VALIDATION_FAILED
}
