package com.simarel.vkbot.receiver.config

import com.simarel.vkbot.receiver.command.sendVkEvent.SendVkEventCommand
import com.simarel.vkbot.receiver.domain.vo.ConfirmationCode
import com.simarel.vkbot.receiver.domain.vo.VkResponse
import com.simarel.vkbot.receiver.port.input.VkConfirmationInputPortResponse
import com.simarel.vkbot.receiver.usecase.ReceiveMessageUsecase
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
@Dependent
class UsecaseConfiguration {

    @Produces
    @Singleton
    fun confirmationCode(confirmationCode: ConfirmationCode, sendVkEventCommand: SendVkEventCommand) = ReceiveMessageUsecase(
        confirmationResponse = VkConfirmationInputPortResponse(VkResponse(confirmationCode.value)),
        okResponse = VkConfirmationInputPortResponse(VkResponse("ok")),
        sendVkEventCommand = sendVkEventCommand,
    )

}