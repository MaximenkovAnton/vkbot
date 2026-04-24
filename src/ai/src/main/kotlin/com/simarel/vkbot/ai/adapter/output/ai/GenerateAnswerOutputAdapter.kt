package com.simarel.vkbot.ai.adapter.output.ai

import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPort
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortRequest
import com.simarel.vkbot.ai.port.output.ai.GenerateAnswerOutputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateAnswerOutputAdapter(
    private val userAnswerAiService: UserAnswerAiService,
) : GenerateAnswerOutputPort {

    override fun execute(request: GenerateAnswerOutputPortRequest): GenerateAnswerOutputPortResponse {
        val answer = userAnswerAiService.generateAnswer(
            memoryId = request.memoryId,
            userMessage = request.userMessage,
            messageContext = request.messageContext,
        )
        return GenerateAnswerOutputPortResponse(answer)
    }
}
