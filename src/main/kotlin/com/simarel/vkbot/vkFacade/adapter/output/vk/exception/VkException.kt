package com.simarel.vkbot.vkFacade.adapter.output.vk.exception

import com.simarel.vkbot.receiver.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class VkException(message: String) : VkBotAppException("Vk exception: $message") {
    override fun status(): ExceptionStatus = ExceptionStatus.EXTERNAL_FAILURE
}
