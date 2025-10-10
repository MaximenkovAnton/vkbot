package com.simarel.vkbot.receiver.domain.exception

import com.simarel.vkbot.share.domain.exception.VkBotAppException

open class ValidationException(message: String, exception: RuntimeException? = null) : VkBotAppException(message, exception) {
    override fun status(): ExceptionStatus = ExceptionStatus.VALIDATION_FAILED
}
