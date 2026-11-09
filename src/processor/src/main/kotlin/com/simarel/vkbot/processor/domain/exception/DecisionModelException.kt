package com.simarel.vkbot.processor.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class DecisionModelException(message: String, cause: RuntimeException) : VkBotAppException(message, cause) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
