package com.simarel.vkbot.receiver.domain.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class VkEventPublishException(cause: Throwable) : VkBotAppException("VK event publish failed", cause) {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
