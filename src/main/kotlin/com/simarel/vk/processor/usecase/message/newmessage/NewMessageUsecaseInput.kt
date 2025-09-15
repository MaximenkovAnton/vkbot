package com.simarel.vk.processor.usecase.message.newmessage

import com.simarel.vk.processor.command.answer.MessageAnswerTextGenerateCommand
import com.simarel.vk.processor.command.answer.MessageAnswerTextGenerateCommandRequest
import com.simarel.vk.processor.command.requireSendMessage.RequireSendMessageCommand
import com.simarel.vk.processor.command.requireSendMessage.RequireSendMessageCommandRequest
import com.simarel.vk.processor.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vk.processor.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPort
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPortRequest
import com.simarel.vk.processor.port.input.messageNew.MessageNewInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class NewMessageUsecaseInput(
    val requireSendMessageCommand: RequireSendMessageCommand
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
            requireSendMessageCommand.execute(
                RequireSendMessageCommandRequest(request.message)
            )
        }
        return okResponse
    }

    private fun requireAnswer(request: MessageNewInputPortRequest): Boolean {
        return true // todo: проверка на наличие прямого вызова или первый ответ принадлежит боту
    }
}