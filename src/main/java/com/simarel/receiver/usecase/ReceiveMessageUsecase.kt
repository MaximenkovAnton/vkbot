package com.simarel.receiver.usecase

import com.simarel.receiver.command.sendVkEvent.PublishVkEventCommandRequest
import com.simarel.receiver.command.sendVkEvent.SendVkEventCommand
import com.simarel.receiver.domain.vo.VkCallbackEvent
import com.simarel.receiver.port.input.ReceiveMessageInputPort
import com.simarel.receiver.port.input.VkConfirmationInputPortRequest
import com.simarel.receiver.port.input.VkConfirmationInputPortResponse
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