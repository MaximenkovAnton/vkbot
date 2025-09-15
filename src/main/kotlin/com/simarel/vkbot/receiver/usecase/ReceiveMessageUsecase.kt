package com.simarel.vkbot.receiver.usecase

import com.simarel.vkbot.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.vkbot.receiver.command.sendVkEvent.SendVkEventCommand
import com.simarel.vkbot.receiver.domain.vo.VkCallbackEvent
import com.simarel.vkbot.receiver.port.input.ReceiveMessageInputPort
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse
import io.quarkus.logging.Log

class ReceiveMessageUsecase(
    val confirmationResponse: VkConfirmationInputPortResponse,
    val okResponse: VkConfirmationInputPortResponse,
    val sendVkEventCommand: SendVkEventCommand,
): ReceiveMessageInputPort {
    override fun execute(request: VkConfirmationInputPortRequest): VkConfirmationInputPortResponse {
        return when(request.vkEvent.type()) {
            VkCallbackEvent.CONFIRMATION -> confirmationResponse
            VkCallbackEvent.UNKNOWN -> {
                Log.error("Unknown event: ${request.vkEvent.value}")
                okResponse
            }
            else -> {
                sendVkEventCommand.execute(
                    PublishVkEventCommandRequest(
                        request.vkEvent
                    )
                )
                okResponse
            }
        }
    }
}