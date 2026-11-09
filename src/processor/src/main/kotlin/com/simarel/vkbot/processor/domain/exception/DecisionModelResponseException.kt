package com.simarel.vkbot.processor.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class DecisionModelResponseException(message: String) : VkBotAppException(message) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
