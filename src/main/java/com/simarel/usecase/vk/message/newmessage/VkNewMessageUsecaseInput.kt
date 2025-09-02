package com.simarel.usecase.vk.message.newmessage

import com.simarel.adapter.output.ai.UserAnswerAiService
import com.simarel.port.input.vk.VkMessageNewInputPort
import com.simarel.port.input.vk.VkMessageNewInputRequest
import com.simarel.port.input.vk.VkMessageNewInputResponse
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputPort
import com.simarel.port.output.ai.AiChatbotAnswerMessageOutputRequest
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkNewMessageUsecaseInput(
    val aiChatbotAnswerMessageOutputPort: AiChatbotAnswerMessageOutputPort
): VkMessageNewInputPort {
    val okResponse = VkMessageNewInputResponse("ok")

    override fun execute(request: VkMessageNewInputRequest): VkMessageNewInputResponse {
        Log.warn(
            aiChatbotAnswerMessageOutputPort.execute(
            AiChatbotAnswerMessageOutputRequest(request.message)
            )
        )
        return okResponse
    }
}
