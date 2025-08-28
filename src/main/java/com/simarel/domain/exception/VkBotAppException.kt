package com.simarel.domain.exception

abstract class VkBotAppException(message: String = "Unhandled Exception on Vk Bot App") : RuntimeException(message) {
    abstract fun status(): ExceptionStatus
}