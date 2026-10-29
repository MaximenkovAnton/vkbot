package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommand
import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse

class ReceiveMessageUsecase(
    private val confirmationResponse: VkConfirmationInputPortResponse,
    private val okResponse: VkConfirmationInputPortResponse,
    private val publishVkEventCommand: PublishVkEventCommand,
) : ReceiveMessageInputPort {
    private val logger = System.getLogger(ReceiveMessageUsecase::class.java.name)

    override fun execute(
        request: VkConfirmationInputPortRequest,
    ): VkConfirmationInputPortResponse = when (request.vkEvent.type) {
        VkCallbackEvent.CONFIRMATION -> confirmationResponse
        VkCallbackEvent.UNKNOWN -> {
            logger.log(System.Logger.Level.ERROR, "Unknown event: ${request.vkEvent.type}")
            okResponse
        }

        else -> {
            request.message?.let { message ->
                publishVkEventCommand.execute(PublishVkEventCommandRequest(message))
            }
            okResponse
        }
    }
}
