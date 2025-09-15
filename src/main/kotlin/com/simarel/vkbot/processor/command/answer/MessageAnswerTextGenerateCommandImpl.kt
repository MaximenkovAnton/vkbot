package com.simarel.vkbot.processor.command.answer

import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.vkbot.processor.port.output.ai.AiChatbotAnswerMessageOutputPortRequest
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