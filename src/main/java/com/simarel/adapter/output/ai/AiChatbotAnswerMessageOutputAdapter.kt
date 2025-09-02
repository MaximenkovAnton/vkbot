package com.simarel.adapter.output.ai

import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputRequest
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AiChatbotAnswerMessageOutputAdapter(val userAnswerAiService: UserAnswerAiService): AiChatbotAnswerMessageOutputPort {
    override fun execute(request: AiChatbotAnswerMessageOutputRequest): AiChatbotAnswerMessageOutputResponse {
        val answer = userAnswerAiService.answerUser(request.message.messageText.value)
        return AiChatbotAnswerMessageOutputResponse(answer)
    }
}