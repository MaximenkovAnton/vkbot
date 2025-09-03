package com.simarel.adapter.output.ai

import com.simarel.domain.vo.MessageText
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AiChatbotAnswerMessageOutputAdapter(val userAnswerAiService: UserAnswerAiService): AiChatbotAnswerMessageOutputPort {
    override fun execute(request: AiChatbotAnswerMessageOutputPortRequest): AiChatbotAnswerMessageOutputPortResponse {
        val answer = userAnswerAiService.answerUser(request.message.messageText.value)
        return AiChatbotAnswerMessageOutputPortResponse(MessageText.of(answer))
    }
}