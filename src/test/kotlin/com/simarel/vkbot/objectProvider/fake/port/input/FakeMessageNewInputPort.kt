package com.simarel.vkbot.objectProvider.fake.port.input

import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeMessageNewInputPort : MessageNewInputPort {
    val executeCalls = ConcurrentLinkedQueue<MessageNewInputPortRequest>()
    private val response = MessageNewInputPortResponse("ok")

    override fun execute(request: MessageNewInputPortRequest): MessageNewInputPortResponse {
        executeCalls.add(request)
        return response
    }
}
