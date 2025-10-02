package com.simarel.vkbot.receiver.domain.exception

import com.simarel.vkbot.share.domain.exception.VkBotAppException

class AccessDeniedException(message: String = "Access Denied") : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.ACCESS_DENIED
}
