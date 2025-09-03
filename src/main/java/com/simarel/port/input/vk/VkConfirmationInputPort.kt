package com.simarel.port.input.vk

import com.simarel.domain.vo.ConfirmationCode

interface VkConfirmationInputPort: VkPort<VkConfirmationInputPortRequest, VkConfirmationInputPortResponse>

class VkConfirmationInputPortRequest: VkPortRequest

@JvmInline
value class VkConfirmationInputPortResponse(val confirmationCode: ConfirmationCode): VkPortResponse