package com.simarel.command.answer

import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageAnswerTextGenerateCommandImpl(
    val aiChatbotAnswerMessageOutputPort: AiChatbotAnswerMessageOutputPort,
): MessageAnswerTextGenerateCommand {
    override fun execute(request: MessageAnswerTextGenerateCommandRequest): MessageAnswerTextGenerateCommandResponse {
        val response = aiChatbotAnswerMessageOutputPort.execute(
            AiChatbotAnswerMessageOutputPortRequest(request.message)
        )
        return MessageAnswerTextGenerateCommandResponse(response.responseMessage)
    }
}