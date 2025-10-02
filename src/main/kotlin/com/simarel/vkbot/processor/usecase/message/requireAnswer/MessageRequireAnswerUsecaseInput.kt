package com.simarel.vkbot.processor.usecase.message.requireAnswer

import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.vkbot.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.processor.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageRequireAnswerUsecaseInput(
    val messageAnswerTextGenerateCommand: MessageAnswerTextGenerateCommand,
    val publishEventCommand: PublishEventCommand
) : MessageRequireAnswerInputPort {
    val okResponse = MessageRequireAnswerInputPortResponse("ok")

    override fun execute(request: MessageRequireAnswerInputPortRequest): MessageRequireAnswerInputPortResponse {
        val initialMessage = request.message
        val aiResponse = messageAnswerTextGenerateCommand.execute(
            MessageAnswerTextGenerateCommandRequest(
                initialMessage
            )
        )
        val answerMessage = initialMessage.answer(aiResponse.messageText)
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.SEND_MESSAGE,
                payload = Payload(answerMessage)
            )
        )
        return okResponse
    }
}
