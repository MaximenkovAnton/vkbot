package com.simarel.vkbot.processor.testfixtures.port.input

import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPort
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortRequest
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeMessageReceivedInputPort : MessageReceivedInputPort {
    val executeCalls = ConcurrentLinkedQueue<MessageReceivedInputPortRequest>()
    private val response = MessageReceivedInputPortResponse("ok")

    override fun execute(request: MessageReceivedInputPortRequest): MessageReceivedInputPortResponse {
        executeCalls.add(request)
        return response
    }
}
