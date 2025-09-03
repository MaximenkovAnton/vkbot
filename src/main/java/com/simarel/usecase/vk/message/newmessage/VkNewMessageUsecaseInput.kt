package com.simarel.usecase.vk.message.newmessage

import com.simarel.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.port.input.vk.VkMessageNewInputPort
import com.simarel.port.input.vk.VkMessageNewInputPortRequest
import com.simarel.port.input.vk.VkMessageNewInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkNewMessageUsecaseInput(
    val messageAnswerTextGenerateCommand: MessageAnswerTextGenerateCommand
): VkMessageNewInputPort {
    val okResponse = VkMessageNewInputPortResponse("ok")

    override fun execute(request: VkMessageNewInputPortRequest): VkMessageNewInputPortResponse {
        messageAnswerTextGenerateCommand.execute(MessageAnswerTextGenerateCommandRequest(request.message))
        return okResponse
    }
}
