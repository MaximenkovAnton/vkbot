package com.simarel.usecase.vk.confirmation

import com.simarel.domain.vo.ConfirmationCode
import com.simarel.port.input.vk.VkConfirmationInputPort
import com.simarel.port.input.vk.VkConfirmationInputRequest
import com.simarel.port.input.vk.VkConfirmationInputResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkConfirmationInputUsecase(val confirmationCode: ConfirmationCode): VkConfirmationInputPort {

    override fun execute(request: VkConfirmationInputRequest): VkConfirmationInputResponse {
        return VkConfirmationInputResponse(confirmationCode)
    }
}