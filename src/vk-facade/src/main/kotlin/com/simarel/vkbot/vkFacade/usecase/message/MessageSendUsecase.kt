package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import com.simarel.vkbot.vkFacade.domain.RandomId
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MessageSendUsecase(private val sendVkMessageCommand: SendVkMessageCommand) : VkSendMessageInputPort {
    private val response = VkSendMessageInputResponse()
    override fun execute(request: VkSendMessageInputRequest): VkSendMessageInputResponse {
        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = request.responseMessage.responseTo.peerId,
                message = request.responseMessage.messageText,
                forwardedMessages = forwardMessagesIfNeeded(request.responseMessage.responseTo, request.forwardedMessages),
                rand = randomId(request.responseMessage.responseTo),
            ),
        )
        return response
    }

    private fun forwardMessagesIfNeeded(responseTo: Message, explicitForwarded: ForwardedMessages?): ForwardedMessages? {
        if (explicitForwarded != null) return explicitForwarded
        if (responseTo.peerId.isHuman()) {
            return null
        }
        return ForwardedMessages(responseTo)
    }

    private fun randomId(responseTo: Message): Int = responseTo.inboxMessageId
        ?.let { RandomId.ofInbox(it).value }
        ?: RandomId.of(responseTo.peerId, responseTo.messageText).value
}
