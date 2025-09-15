package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageSendUsecase(val sendVkMessageCommand: SendVkMessageCommand): VkSendMessageInputPort {
    val response = VkSendMessageInputResponse()
    override fun execute(request: VkSendMessageInputRequest): VkSendMessageInputResponse {
        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = request.peerId,
                message = request.messageText
            )
        )
        return response
    }
}