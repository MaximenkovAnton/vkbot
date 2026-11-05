package com.simarel.vkbot.receiver.fixtures.port

import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeReceiveMessageInputPort(
    val journal: MutableList<String> = mutableListOf(),
) : ReceiveMessageInputPort {
    val executeCalls = ConcurrentLinkedQueue<VkUpdateInputPortRequest>()
    var failure: RuntimeException? = null

    override fun execute(request: VkUpdateInputPortRequest): VkUpdateInputPortResponse {
        executeCalls.add(request)
        failure?.let { throw it }
        journal.add(PUBLISH)
        return VkUpdateInputPortResponse
    }

    private companion object {
        const val PUBLISH = "publish"
    }
}
