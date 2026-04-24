package com.simarel.vkbot.ai.usecase.message.requireAnswer

import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommand
import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommandRequest
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPort
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortRequest
import com.simarel.vkbot.ai.port.input.messageRequireAnswer.MessageRequireAnswerInputPortResponse
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.vo.Payload
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class GenerateAnswerUsecase(
    private val generateAnswerCommand: GenerateAnswerCommand,
    private val publishEventCommand: PublishEventCommand,
) : MessageRequireAnswerInputPort {

    private val okResponse = MessageRequireAnswerInputPortResponse("ok")

    override fun execute(request: MessageRequireAnswerInputPortRequest): MessageRequireAnswerInputPortResponse {
        val initialMessage = request.message
        val answer = generateAnswerCommand.execute(
            GenerateAnswerCommandRequest(initialMessage),
        ).responseText
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.ANSWER_MESSAGE,
                payload = Payload(
                    ResponseMessage(
                        messageText = answer,
                        responseTo = initialMessage,
                    ),
                ),
            ),
        )
        return okResponse
    }
}
