package com.simarel.receiver.domain.exception

import com.simarel.share.domain.exception.VkBotAppException

class AccessDeniedException(message: String = "Access Denied") : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.ACCESS_DENIED
}