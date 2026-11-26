package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.VkUpdateType
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.share.domain.model.Message

object FakeVkUpdateInputPortProvider {

    const val RAW_UPDATE = """{"type":"message_new","object":{"message":{"text":"hello"}},"group_id":1}"""

    fun createRequest(
        type: VkUpdateType = VkUpdateType.MESSAGE_NEW,
        message: Message? = null,
        rawUpdate: String = RAW_UPDATE,
    ) = VkUpdateInputPortRequest(type = type, message = message, rawUpdate = rawUpdate)
}
