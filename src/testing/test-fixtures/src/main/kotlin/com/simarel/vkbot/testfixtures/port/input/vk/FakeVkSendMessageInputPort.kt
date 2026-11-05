package com.simarel.vkbot.testfixtures.port.input.vk

import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeVkSendMessageInputPort : VkSendMessageInputPort {
    val executeCalls = ConcurrentLinkedQueue<VkSendMessageInputRequest>()
    private val response = VkSendMessageInputResponse()

    override fun execute(request: VkSendMessageInputRequest): VkSendMessageInputResponse {
        executeCalls.add(request)
        return response
    }
}
