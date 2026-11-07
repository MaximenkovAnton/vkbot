package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.domain.RandomId
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendPlainMessageInputPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PlainMessageSendUsecase(
    private val sendVkMessageCommand: SendVkMessageCommand,
) : VkSendPlainMessageInputPort {
    private val response = VkSendPlainMessageInputPort.Response

    override fun execute(request: VkSendPlainMessageInputPort.Request): VkSendPlainMessageInputPort.Response {
        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = request.peerId,
                message = request.messageText,
                forwardedMessages = null,
                rand = RandomId.of(request.peerId, request.messageText).value,
            ),
        )
        return response
    }
}
