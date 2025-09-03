package com.simarel.command.answer

import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputRequest
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageAnswerTextGenerateCommandImpl(
    val aiChatbotAnswerMessageOutputPort: AiChatbotAnswerMessageOutputPort,
): MessageAnswerTextGenerateCommand {
    override fun execute(request: MessageAnswerTextGenerateCommandRequest): MessageAnswerTextGenerateCommandResponse {
        val response = aiChatbotAnswerMessageOutputPort.execute(
            AiChatbotAnswerMessageOutputRequest(request.message)
        )
        Log.warn("Ai generated answer text: ${response.responseMessage}")
        return MessageAnswerTextGenerateCommandResponse(response.responseMessage)
    }
}