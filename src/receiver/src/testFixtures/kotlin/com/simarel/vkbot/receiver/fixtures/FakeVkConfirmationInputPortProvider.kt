package com.simarel.vkbot.receiver.fixtures

import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.receiver.domain.vo.VkResponse
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse

object FakeVkConfirmationInputPortProvider {
    fun createRequest(vkEvent: VkEvent? = null) = VkConfirmationInputPortRequest(
        vkEvent ?: FakeVkProvider.createVkEvent(),
    )

    fun createConfirmationResponse(value: String? = null) = VkConfirmationInputPortResponse(
        VkResponse(value ?: "123456"),
    )

    fun createOkResponse(value: String? = null) = VkConfirmationInputPortResponse(
        VkResponse(value ?: "ok"),
    )
}
