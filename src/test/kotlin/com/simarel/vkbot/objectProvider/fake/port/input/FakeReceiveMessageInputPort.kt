package com.simarel.vkbot.objectProvider.fake.port.input

import com.simarel.vkbot.receiver.domain.vo.VkResponse
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeReceiveMessageInputPort(
    val configuredResponse: VkConfirmationInputPortResponse? = null,
) : ReceiveMessageInputPort {
    val executeCalls = ConcurrentLinkedQueue<VkConfirmationInputPortRequest>()
    private val defaultResponse = VkConfirmationInputPortResponse(VkResponse("ok"))

    override fun execute(request: VkConfirmationInputPortRequest): VkConfirmationInputPortResponse {
        executeCalls.add(request)
        return configuredResponse ?: defaultResponse
    }
}
