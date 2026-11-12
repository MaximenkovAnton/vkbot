package com.simarel.vkbot.ai.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class ChatKnowledgeSummaryException(message: String, cause: Throwable?) : VkBotAppException(message, cause) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
