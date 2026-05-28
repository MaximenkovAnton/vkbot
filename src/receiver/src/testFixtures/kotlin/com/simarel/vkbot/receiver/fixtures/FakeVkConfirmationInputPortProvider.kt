package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.receiver.domain.vo.VkResponse
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse
import com.simarel.vkbot.share.domain.model.Message

object FakeVkConfirmationInputPortProvider {
    fun createRequest(vkEvent: VkEvent? = null, message: Message? = null) = VkConfirmationInputPortRequest(
        vkEvent ?: FakeVkProvider.createVkEvent(),
        message = message,
    )

    fun createConfirmationResponse(value: String? = null) = VkConfirmationInputPortResponse(
        VkResponse(value ?: "123456"),
    )

    fun createOkResponse(value: String? = null) = VkConfirmationInputPortResponse(
        VkResponse(value ?: "ok"),
    )
}
