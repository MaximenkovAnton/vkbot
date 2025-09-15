package com.simarel.vk.receiver.domain.exception

import com.simarel.vk.share.domain.exception.VkBotAppException

class AccessDeniedException(message: String = "Access Denied") : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.ACCESS_DENIED
}