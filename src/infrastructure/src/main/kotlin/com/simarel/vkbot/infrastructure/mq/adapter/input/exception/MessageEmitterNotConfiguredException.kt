package com.simarel.vkbot.infrastructure.mq.adapter.input.exception

import com.simarel.vkbot.share.domain.exception.ExceptionStatus
import com.simarel.vkbot.share.domain.exception.VkBotAppException

class MessageEmitterNotConfiguredException(queueName: String) :
    VkBotAppException(
        "No emitter configured for queue: $queueName",
    ) {
    override fun status(): ExceptionStatus = ExceptionStatus.UNKNOWN
}
