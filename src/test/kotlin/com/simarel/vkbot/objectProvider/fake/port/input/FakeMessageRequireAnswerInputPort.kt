package com.simarel.vkbot.objectProvider.fake.port.input

import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeMessageRequireAnswerInputPort : MessageRequireAnswerInputPort {
    val executeCalls = ConcurrentLinkedQueue<MessageRequireAnswerInputPortRequest>()
    private val response = MessageRequireAnswerInputPortResponse("ok")

    override fun execute(request: MessageRequireAnswerInputPortRequest): MessageRequireAnswerInputPortResponse {
        executeCalls.add(request)
        return response
    }
}
