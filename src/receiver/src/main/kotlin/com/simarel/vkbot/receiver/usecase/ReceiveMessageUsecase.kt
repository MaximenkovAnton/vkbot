package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommand
import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkUpdateInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ReceiveMessageUsecase(
    private val publishVkEventCommand: PublishVkEventCommand,
) : ReceiveMessageInputPort {

    override fun execute(request: VkUpdateInputPortRequest): VkUpdateInputPortResponse {
        request.message?.let { message ->
            publishVkEventCommand.execute(PublishVkEventCommandRequest(message))
        }
        return VkUpdateInputPortResponse
    }
}
