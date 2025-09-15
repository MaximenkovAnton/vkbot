package com.simarel.vkbot.processor.adapter.output.ai

import com.simarel.vkbot.processor.domain.vo.MessageText
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AiChatbotAnswerMessageOutputAdapter(val userAnswerAiService: UserAnswerAiService): AiChatbotAnswerMessageOutputPort {
    override fun execute(request: AiChatbotAnswerMessageOutputPortRequest): AiChatbotAnswerMessageOutputPortResponse {
        val answer = userAnswerAiService.answerUser(request.message.messageText.value)
        return AiChatbotAnswerMessageOutputPortResponse(MessageText.of(answer))
    }
}