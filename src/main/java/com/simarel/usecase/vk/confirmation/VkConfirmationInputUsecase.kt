package com.simarel.usecase.vk.confirmation

import com.simarel.domain.vo.ConfirmationCode
import com.simarel.port.input.vk.VkConfirmationInputPort
import com.simarel.port.input.vk.VkConfirmationInputPortRequest
import com.simarel.port.input.vk.VkConfirmationInputPortResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkConfirmationInputUsecase(val confirmationCode: ConfirmationCode): VkConfirmationInputPort {

    override fun execute(request: VkConfirmationInputPortRequest): VkConfirmationInputPortResponse {
        return VkConfirmationInputPortResponse(confirmationCode)
    }
}