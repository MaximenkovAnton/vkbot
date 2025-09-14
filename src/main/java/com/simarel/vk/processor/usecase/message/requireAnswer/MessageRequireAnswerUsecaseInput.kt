package com.simarel.vk.processor.usecase.message.requireAnswer

import com.simarel.vk.processor.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.vk.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vk.processor.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vk.processor.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vk.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vk.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import com.simarel.vk.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageRequireAnswerUsecaseInput(
    val messageAnswerTextGenerateCommand: MessageAnswerTextGenerateCommand,
    val sendVkMessageCommand: SendVkMessageCommand
): MessageRequireAnswerInputPort {
    val okResponse = MessageRequireAnswerInputPortResponse("ok")

    override fun execute(request: MessageRequireAnswerInputPortRequest): MessageRequireAnswerInputPortResponse {
        val aiResponse = messageAnswerTextGenerateCommand.execute(
            MessageAnswerTextGenerateCommandRequest(
                request.message
            )
        )
        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = request.message.peerId,
                message = aiResponse.messageText
            )
        )
        return okResponse
    }
}