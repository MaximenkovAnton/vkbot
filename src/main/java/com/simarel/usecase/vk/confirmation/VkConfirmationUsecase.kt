package com.simarel.usecase.vk.confirmation

import com.simarel.domain.vo.ConfirmationCode
import com.simarel.port.input.vk.callback.VkConfirmationPort
import com.simarel.port.input.vk.callback.VkConfirmationRequest
import com.simarel.port.input.vk.callback.VkConfirmationResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
open class VkConfirmationUsecase(val confirmationCode: ConfirmationCode): VkConfirmationPort {

    override fun execute(request: VkConfirmationRequest): VkConfirmationResponse {
        return VkConfirmationResponse(confirmationCode)
    }
}