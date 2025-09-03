package com.simarel.usecase.vk.message.newmessage

import com.simarel.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.port.input.vk.VkMessageNewInputPort
import com.simarel.port.input.vk.VkMessageNewInputRequest
import com.simarel.port.input.vk.VkMessageNewInputResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkNewMessageUsecaseInput(
    val messageAnswerTextGenerateCommand: MessageAnswerTextGenerateCommand
): VkMessageNewInputPort {
    val okResponse = VkMessageNewInputResponse("ok")

    override fun execute(request: VkMessageNewInputRequest): VkMessageNewInputResponse {
        messageAnswerTextGenerateCommand.execute(MessageAnswerTextGenerateCommandRequest(request.message))
        return okResponse
    }
}
