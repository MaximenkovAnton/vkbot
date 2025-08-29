package com.simarel.domain.exception

class AccessDeniedException(message: String = "Access Denied") : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.ACCESS_DENIED
}