package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommand
import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortResponse
import com.simarel.vkbot.share.port.output.persistence.RegisterIncomingMessagePort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ReceiveMessageUsecase(
    private val registerIncomingMessagePort: RegisterIncomingMessagePort,
    private val publishVkEventCommand: PublishVkEventCommand,
) : ReceiveMessageInputPort {

    override fun execute(request: VkUpdateInputPortRequest): VkUpdateInputPortResponse {
        request.message?.let { message ->
            val registered = registerIncomingMessagePort.execute(
                RegisterIncomingMessagePort.Request(
                    peerId = message.peerId,
                    conversationMessageId = message.conversationMessageId,
                    payload = request.rawUpdate,
                ),
            )
            publishVkEventCommand.execute(
                PublishVkEventCommandRequest(message.copy(inboxMessageId = registered.inboxMessageId)),
            )
        }
        return VkUpdateInputPortResponse
    }
}
