package com.simarel.port.input.vk.callback

import com.simarel.domain.vo.ConfirmationCode

interface VkConfirmationPort: VkPort<VkConfirmationRequest, VkConfirmationResponse>

class VkConfirmationRequest: VkRequest

@JvmInline
value class VkConfirmationResponse(val confirmationCode: ConfirmationCode): VkResponse