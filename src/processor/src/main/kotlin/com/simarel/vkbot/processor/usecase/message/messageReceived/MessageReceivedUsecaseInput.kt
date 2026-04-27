package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPort
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortRequest
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortResponse
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class MessageReceivedUsecaseInput(
    private val publishEventCommand: PublishEventCommand,
    private val isRequireAnswerCommand: IsRequireAnswerCommand,
) : MessageReceivedInputPort {
    private val okResponse = MessageReceivedInputPortResponse("ok")

    override fun execute(request: MessageReceivedInputPortRequest): MessageReceivedInputPortResponse {
        /*
        TODO: Добавить контекст
        1) сохранить сообщение в бд
        2) сохранить сообщение в rag
        3) достать подходящие сообщения из rag
        4) достать n последних сообщений из бд
        5) использовать данные как контекст для ответа на вопрос
         */
        // TODO: Собирать профиль пользователей на основе сообщений в чатах
        val message = request.message
        if (isRequireAnswerCommand.execute(message)) {
            publishEventCommand.execute(
                PublishEventRequest(
                    event = Event.MESSAGE_REQUIRE_ANSWER,
                    payload = Payload(message),
                ),
            )
        }
        return okResponse
    }
}
