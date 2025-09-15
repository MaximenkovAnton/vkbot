package com.simarel.vkbot.processor.usecase.message.newmessage

import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vkbot.processor.port.input.messageNew.MessageNewInputPortResponse
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class NewMessageUsecaseInput(
    val publishEventCommand: PublishEventCommand
): MessageNewInputPort {
    val okResponse = MessageNewInputPortResponse("ok")

    override fun execute(request: MessageNewInputPortRequest): MessageNewInputPortResponse {
        /*
        TODO: Добавить контекст
        1) сохранить сообщение в бд
        2) сохранить сообщение в rag
        3) достать подходящие сообщения из rag
        4) достать n последних сообщений из бд
        5) использовать данные как контекст для ответа на вопрос
         */
        // TODO: Собирать профиль пользователей на основе сообщений в чатах
        if(requireAnswer(request)) {
            publishEventCommand.execute(
                PublishEventRequest(
                    event = Event.MESSAGE_REQUIRE_ANSWER,
                    payload = Payload(request.message)
                )
            )
        }
        return okResponse
    }

    private fun requireAnswer(request: MessageNewInputPortRequest): Boolean {
        return true // todo: проверка на наличие прямого вызова или первый ответ принадлежит боту
    }
}