package com.simarel.vkbot.objectProvider.fake.adapter.output.ai

import com.simarel.vkbot.objectProvider.fake.domain.FakeVoProvider
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import java.util.concurrent.ConcurrentLinkedQueue

class FakeAiChatbotAnswerMessageOutputPort : AiChatbotAnswerMessageOutputPort {

    val executeCalls = ConcurrentLinkedQueue<AiChatbotAnswerMessageOutputPortRequest>()

    override fun execute(request: AiChatbotAnswerMessageOutputPortRequest): AiChatbotAnswerMessageOutputPortResponse {
        executeCalls.add(request)
        return AiChatbotAnswerMessageOutputPortResponse(FakeVoProvider.createMessageText())
    }
}
