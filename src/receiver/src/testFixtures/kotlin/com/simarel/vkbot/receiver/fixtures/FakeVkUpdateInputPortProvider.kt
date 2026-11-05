package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeVkUpdateInputPortProvider {

    fun createRequest(
        type: VkUpdateType = VkUpdateType.MESSAGE_NEW,
        message: Message? = null,
    ) = VkUpdateInputPortRequest(type = type, message = message)
}
