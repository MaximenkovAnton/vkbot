package com.simarel.vkbot.vkFacade.usecase.message

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputResponse
import jakarta.enterprise.context.ApplicationScoped
import java.nio.ByteBuffer
import java.security.MessageDigest

@ApplicationScoped
class MessageSendUsecase(private val sendVkMessageCommand: SendVkMessageCommand) : VkSendMessageInputPort {
    private val response = VkSendMessageInputResponse()
    override fun execute(request: VkSendMessageInputRequest): VkSendMessageInputResponse {
        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = request.responseMessage.responseTo.peerId,
                message = request.responseMessage.messageText,
                forwardedMessages = forwardMessagesIfNeeded(request.responseMessage.responseTo),
                rand = countRand(request.responseMessage.responseTo)
            ),
        )
        return response
    }

    private fun forwardMessagesIfNeeded(responseTo: Message): ForwardedMessages? {
        if(responseTo.peerId.isHuman()) {
            return null
        }
        return ForwardedMessages(responseTo)
    }

    val digest = MessageDigest.getInstance("SHA-256")
    private fun countRand(responseTo: Message): Int {
        val uniqueString = responseTo.messageText.value + responseTo.peerId.value + responseTo.date.value.toEpochSecond()
        val hashBytes = digest.digest(uniqueString.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(hashBytes).getInt()
    }
}
