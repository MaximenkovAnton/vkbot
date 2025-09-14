package com.simarel.vk.processor.adapter.output.ai

import com.simarel.vk.processor.domain.vo.MessageText
import com.simarel.vk.processor.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.vk.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vk.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AiChatbotAnswerMessageOutputAdapter(val userAnswerAiService: UserAnswerAiService): AiChatbotAnswerMessageOutputPort {
    override fun execute(request: AiChatbotAnswerMessageOutputPortRequest): AiChatbotAnswerMessageOutputPortResponse {
        val answer = userAnswerAiService.answerUser(request.message.messageText.value)
        return AiChatbotAnswerMessageOutputPortResponse(MessageText.of(answer))
    }
}